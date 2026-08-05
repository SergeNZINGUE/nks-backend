package bf.laterrasse.nks.service;

import bf.laterrasse.nks.config.PaymentProperties;
import bf.laterrasse.nks.domain.Paiement;
import bf.laterrasse.nks.domain.TransactionMobileMoney;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.OperateurMobileMoney;
import bf.laterrasse.nks.domain.enums.Enums.StatutPaiement;
import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import bf.laterrasse.nks.dto.paiement.InitierPaiementRequest;
import bf.laterrasse.nks.dto.paiement.InitierPaiementResponse;
import bf.laterrasse.nks.event.PaiementConfirmeEvent;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.gateway.payment.ConfirmationPaiement;
import bf.laterrasse.nks.gateway.payment.InitiationPaiement;
import bf.laterrasse.nks.gateway.payment.PaymentGateway;
import bf.laterrasse.nks.repository.PaiementRepository;
import bf.laterrasse.nks.repository.TransactionMobileMoneyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * WF-03/WF-04/WF-10 : initiation et confirmation webhook des paiements Mobile Money.
 * L'idempotence est garantie par la contrainte UNIQUE sur paiements.idempotency_key
 * (§10.6) et par la déduplication des références opérateur en base
 * (transactions_mobile_money.reference_operateur, §14.7).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaiementService {

    private final PaiementRepository paiementRepository;
    private final TransactionMobileMoneyRepository transactionRepository;
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
     * Crée le Paiement (PENDING) et démarre la transaction côté gateway. Utilisé directement
     * par les autres modules (VoteService, BilletterieService) qui doivent conserver la
     * référence au Paiement pour lier leurs propres entités (VotePayant, Reservation) avant
     * même la confirmation — cf. §10.6/10.7.
     */
    @Transactional
    public PaiementInitie creerEtDemarrer(TypePaiement type, java.math.BigDecimal montant, String telephone, Utilisateur utilisateurOuNull) {
        Paiement paiement = Paiement.builder()
                .utilisateur(utilisateurOuNull)
                .typePaiement(type)
                .montant(montant)
                .statut(StatutPaiement.PENDING)
                .idempotencyKey(UUID.randomUUID())
                .build();
        paiement = paiementRepository.save(paiement);

        String callbackUrl = paymentProperties.getLigdicash().getCallbackBaseUrl() + "/webhooks/ligdicash";
        InitiationPaiement initiation = paymentGateway.initierPaiement(
                montant, telephone, paiement.getIdempotencyKey().toString(), callbackUrl);

        paiement.setReferenceExterne(initiation.referenceOperateur());
        paiement = paiementRepository.save(paiement);
        return new PaiementInitie(paiement, initiation.urlPaiement());
    }

    /**
     * Point d'entrée du webhook LigdiCash (§13.7). Doit rester rapide : la logique métier
     * déclenchée (activation profil, crédit de votes, confirmation billet) est déléguée
     * aux listeners de {@link PaiementConfirmeEvent} plutôt que traitée ici.
     */
    @Transactional
    public void traiterWebhook(String payload, String signature) {
        if (!paymentGateway.verifierWebhook(payload, signature)) {
            log.warn("Webhook LigdiCash rejeté : signature invalide");
            throw new ValidationMetierException("Signature webhook invalide");
        }

        ConfirmationPaiement confirmation = paymentGateway.traiterConfirmation(payload);
        if (confirmation.idempotencyKey() == null) {
            log.warn("Webhook LigdiCash sans client_reference exploitable — payload ignoré");
            return;
        }

        Paiement paiement = paiementRepository.findByIdempotencyKey(UUID.fromString(confirmation.idempotencyKey()))
                .orElse(null);
        if (paiement == null) {
            log.warn("Webhook LigdiCash : aucun paiement pour idempotency_key={}", confirmation.idempotencyKey());
            return;
        }

        if (paiement.getStatut() == StatutPaiement.COMPLETED) {
            log.info("Webhook LigdiCash dupliqué pour paiement {} — ignoré (idempotence)", paiement.getId());
            return; // déjà traité, on répond 200 sans rien refaire (§14.7)
        }

        // Déduplication par référence opérateur (contrainte UNIQUE operateur+reference_operateur)
        Optional<TransactionMobileMoney> dejaTraitee = confirmation.referenceOperateur() == null ? Optional.empty()
                : transactionRepository.findByOperateurAndReferenceOperateur(
                        OperateurMobileMoney.LIGDICASH, confirmation.referenceOperateur());
        if (dejaTraitee.isPresent()) {
            log.info("Transaction LigdiCash {} déjà enregistrée — ignorée", confirmation.referenceOperateur());
            return;
        }

        TransactionMobileMoney transaction = TransactionMobileMoney.builder()
                .paiement(paiement)
                .operateur(OperateurMobileMoney.LIGDICASH)
                .referenceOperateur(confirmation.referenceOperateur() != null
                        ? confirmation.referenceOperateur() : "UNKNOWN-" + UUID.randomUUID())
                .montant(confirmation.montant() != null ? confirmation.montant() : paiement.getMontant())
                .telephonePayeur(confirmation.telephonePayeur())
                .statutOperateur(confirmation.statutOperateur())
                .webhookPayload(payload)
                .signatureWebhook(signature)
                .dateWebhook(Instant.now())
                .build();
        transactionRepository.save(transaction);

        paiement.setStatut(confirmation.succes() ? StatutPaiement.COMPLETED : StatutPaiement.FAILED);
        paiement.setDateFinalisation(Instant.now());
        paiementRepository.save(paiement);

        if (confirmation.succes()) {
            eventPublisher.publishEvent(new PaiementConfirmeEvent(
                    paiement.getId(), paiement.getTypePaiement(),
                    paiement.getUtilisateur() != null ? paiement.getUtilisateur().getId() : null,
                    paiement.getMontant(), confirmation.referenceOperateur()));
        } else {
            log.info("Paiement {} échoué côté opérateur (statut={})", paiement.getId(), confirmation.statutOperateur());
            // La relance automatique (RM-14) est gérée par NotificationRetryJob / logique candidat (US-09)
            eventPublisher.publishEvent(new bf.laterrasse.nks.event.PaiementEchoueEvent(paiement.getId(), paiement.getTypePaiement()));
        }
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "PAIEMENT_CONFIRME_MANUELLEMENT", entite = "Paiement")
    public Paiement confirmerManuellement(UUID paiementId, String referenceManuelle) {
        Paiement paiement = paiementRepository.findById(paiementId)
                .orElseThrow(() -> new bf.laterrasse.nks.exception.ResourceNotFoundException("Paiement introuvable"));
        paiement.setStatut(StatutPaiement.COMPLETED);
        paiement.setManuel(true);
        paiement.setReferenceExterne(referenceManuelle);
        paiement.setDateFinalisation(Instant.now());
        paiementRepository.save(paiement);

        eventPublisher.publishEvent(new PaiementConfirmeEvent(
                paiement.getId(), paiement.getTypePaiement(),
                paiement.getUtilisateur() != null ? paiement.getUtilisateur().getId() : null,
                paiement.getMontant(), referenceManuelle));

        return paiement;
    }
}
