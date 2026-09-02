package bf.laterrasse.nks.service;

import bf.laterrasse.nks.config.PaymentProperties;
import bf.laterrasse.nks.domain.LigdiCashCallback;
import bf.laterrasse.nks.domain.Paiement;
import bf.laterrasse.nks.domain.TransactionMobileMoney;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.OperateurMobileMoney;
import bf.laterrasse.nks.domain.enums.Enums.StatutPaiement;
import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import bf.laterrasse.nks.dto.paiement.InitierPaiementRequest;
import bf.laterrasse.nks.dto.paiement.InitierPaiementResponse;
import bf.laterrasse.nks.event.PaiementConfirmeEvent;
import bf.laterrasse.nks.event.PaiementEchoueEvent;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.gateway.payment.ConfirmationPaiement;
import bf.laterrasse.nks.gateway.payment.InitiationPaiement;
import bf.laterrasse.nks.gateway.payment.PaymentGateway;
import bf.laterrasse.nks.repository.LigdiCashCallbackRepository;
import bf.laterrasse.nks.repository.PaiementRepository;
import bf.laterrasse.nks.repository.TransactionMobileMoneyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * WF-03/WF-04/WF-10 : initiation et confirmation webhook des paiements Mobile Money.
 *
 * Protocole LigdiCash (source de vérité = confirmInvoice) :
 *  1. createInvoice → PENDING en base + token stocké dans referenceExterne
 *  2. Callback reçu → extraire token, déduplication atomique (ligdicash_callbacks),
 *     appeler confirmInvoice, agir sur la réponse — jamais sur le contenu du callback lui-même
 *  3. Polling de secours (PaiementPollingJob) → si PENDING > 2 min sans callback
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaiementService {

    private static final int MAX_TENTATIVES_POLLING = 10;

    @Value("${nks.frontend-base-url}")
    private String frontendBaseUrl;

    private final PaiementRepository paiementRepository;
    private final TransactionMobileMoneyRepository transactionRepository;
    private final LigdiCashCallbackRepository callbackRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentProperties paymentProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InitierPaiementResponse initier(InitierPaiementRequest request, Utilisateur utilisateur) {
        PaiementInitie initie = creerEtDemarrer(request.typePaiement(), request.montant(), request.telephone(), utilisateur);
        return new InitierPaiementResponse(initie.paiement().getId(), initie.urlPaiement(),
                paymentProperties.getPrereservationTimeoutMinutes() * 60L);
    }

    /**
     * Crée le Paiement (PENDING) et démarre la transaction côté gateway.
     * Le token LigdiCash est stocké dans referenceExterne pour les appels confirm ultérieurs.
     */
    @Transactional
    public PaiementInitie creerEtDemarrer(TypePaiement type, java.math.BigDecimal montant,
                                           String telephone, Utilisateur utilisateurOuNull) {
        Paiement paiement = Paiement.builder()
                .utilisateur(utilisateurOuNull)
                .typePaiement(type)
                .montant(montant)
                .statut(StatutPaiement.PENDING)
                .idempotencyKey(UUID.randomUUID())
                .build();
        paiement = paiementRepository.save(paiement);

        String notifyUrl = paymentProperties.getLigdicash().getCallbackBaseUrl() + "/webhooks/ligdicash";
        String returnUrl = frontendBaseUrl + "/paiement/retour?paiementId=" + paiement.getId();
        String cancelUrl = frontendBaseUrl + "/paiement/retour?paiementId=" + paiement.getId() + "&annule=true";

        InitiationPaiement initiation = paymentGateway.initierPaiement(
                montant, telephone, paiement.getIdempotencyKey().toString(),
                notifyUrl, returnUrl, cancelUrl);

        // referenceExterne = token LigdiCash, utilisé pour tous les appels confirm suivants
        paiement.setReferenceExterne(initiation.referenceOperateur());
        paiement = paiementRepository.save(paiement);
        return new PaiementInitie(paiement, initiation.urlPaiement());
    }

    /**
     * Point d'entrée du webhook LigdiCash (§13.7).
     * LigdiCash envoie 2 POST par événement — la déduplication atomique via
     * ligdicash_callbacks garantit un traitement unique. On ne se fie jamais au contenu
     * du callback : on appelle confirmInvoice pour obtenir le statut réel.
     */
    @Transactional
    public void traiterWebhook(String payload) {
        String token = paymentGateway.extraireTokenWebhook(payload);
        if (token == null) {
            log.warn("Webhook LigdiCash : token introuvable — payload ignoré");
            return;
        }

        Paiement paiement = paiementRepository.findByReferenceExterne(token).orElse(null);
        if (paiement == null) {
            log.warn("LigdiCash webhook : aucun paiement trouvé pour token={}", token);
            return;
        }

        if (paiement.getStatut() == StatutPaiement.COMPLETED) {
            log.info("Paiement {} déjà COMPLETED — ignoré (idempotence)", paiement.getId());
            return;
        }

        try {
            callbackRepository.saveAndFlush(new LigdiCashCallback(token));
        } catch (DataIntegrityViolationException e) {
            log.info("Callback {} déjà enregistré — doublon ignoré (idempotence atomique)", token);
            return;
        }

        traiterConfirmationToken(token, payload);
    }

    /** Appelé par le polling de secours (PaiementPollingJob) pour les PENDING bloqués. */
    @Transactional
    public void interrogerStatutParPolling(UUID paiementId) {
        Paiement paiement = paiementRepository.findById(paiementId).orElse(null);
        if (paiement == null || paiement.getStatut() != StatutPaiement.PENDING) return;

        String token = paiement.getReferenceExterne();
        if (token == null) {
            log.warn("Polling paiement {} sans token LigdiCash — ignoré", paiementId);
            return;
        }

        paiement.setNbTentativesPolling(paiement.getNbTentativesPolling() + 1);
        paiement.setDerniereTentativePolling(Instant.now());

        if (paiement.getNbTentativesPolling() >= MAX_TENTATIVES_POLLING) {
            paiement.setStatut(StatutPaiement.EXPIRED);
            paiementRepository.save(paiement);
            log.warn("Paiement {} expiré après {} tentatives de polling sans confirmation", paiementId, MAX_TENTATIVES_POLLING);
            eventPublisher.publishEvent(new PaiementEchoueEvent(paiement.getId(), paiement.getTypePaiement()));
            return;
        }

        paiementRepository.save(paiement);
        traiterConfirmationToken(token, null);
    }

    private void traiterConfirmationToken(String token, String payloadBrut) {
        Paiement paiement = paiementRepository.findByReferenceExterneForUpdate(token).orElse(null);
        if (paiement == null) {
            log.warn("LigdiCash : aucun paiement pour token={}", token);
            return;
        }

        if (paiement.getStatut() == StatutPaiement.COMPLETED) {
            log.info("Paiement {} déjà COMPLETED — ignoré (idempotence)", paiement.getId());
            return;
        }

        // Source de vérité : appeler confirmInvoice
        ConfirmationPaiement confirmation = paymentGateway.confirmerPaiement(token);

        TransactionMobileMoney transaction = TransactionMobileMoney.builder()
                .paiement(paiement)
                .operateur(OperateurMobileMoney.LIGDICASH)
                .referenceOperateur(token)
                .tokenCreation(token)
                .montant(confirmation.montant() != null ? confirmation.montant() : paiement.getMontant())
                .telephonePayeur(confirmation.telephonePayeur())
                .statutOperateur(confirmation.statutOperateur())
                .codeReponse(confirmation.codeReponse())
                .motifRejet(confirmation.motifRejet())
                .webhookPayload(payloadBrut)
                .dateWebhook(Instant.now())
                .build();
        transactionRepository.save(transaction);

        if (confirmation.succes()) {
            paiement.setStatut(StatutPaiement.COMPLETED);
            paiement.setDateFinalisation(Instant.now());
            paiementRepository.save(paiement);
            eventPublisher.publishEvent(new PaiementConfirmeEvent(
                    paiement.getId(), paiement.getTypePaiement(),
                    paiement.getUtilisateur() != null ? paiement.getUtilisateur().getId() : null,
                    paiement.getMontant(), token, confirmation.telephonePayeur()));
        } else if ("notcompleted".equalsIgnoreCase(confirmation.statutOperateur())) {
            paiement.setStatut(StatutPaiement.FAILED);
            paiement.setDateFinalisation(Instant.now());
            paiementRepository.save(paiement);
            log.info("Paiement {} échoué — code={} motif={}", paiement.getId(),
                    confirmation.codeReponse(), confirmation.motifRejet());
            eventPublisher.publishEvent(new PaiementEchoueEvent(paiement.getId(), paiement.getTypePaiement()));
        } else if ("pending".equalsIgnoreCase(confirmation.statutOperateur())) {
            log.info("Paiement {} toujours PENDING côté LigdiCash (code={}) — en attente de finalisation par le client",
                    paiement.getId(), confirmation.codeReponse());
        }
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "PAIEMENT_CONFIRME_MANUELLEMENT", entite = "Paiement")
    public Paiement confirmerManuellement(UUID paiementId, String referenceManuelle) {
        Paiement paiement = paiementRepository.findById(paiementId)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable"));
        paiement.setStatut(StatutPaiement.COMPLETED);
        paiement.setManuel(true);
        paiement.setReferenceExterne(referenceManuelle);
        paiement.setDateFinalisation(Instant.now());
        paiementRepository.save(paiement);

        eventPublisher.publishEvent(new PaiementConfirmeEvent(
                paiement.getId(), paiement.getTypePaiement(),
                paiement.getUtilisateur() != null ? paiement.getUtilisateur().getId() : null,
                paiement.getMontant(), referenceManuelle, null));

        return paiement;
    }
}
