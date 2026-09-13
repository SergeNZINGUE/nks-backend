package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.DroitVoteSurPlace;
import bf.laterrasse.nks.domain.Duo;
import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.Vote;
import bf.laterrasse.nks.domain.enums.Enums.ResultatScan;
import bf.laterrasse.nks.domain.enums.Enums.StatutDroitVote;
import bf.laterrasse.nks.domain.enums.Enums.TypeVote;
import bf.laterrasse.nks.dto.scan.ScanResponse;
import bf.laterrasse.nks.dto.votesurplace.DroitVoteResponse;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.gateway.sms.WhatsappGateway;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.DroitVoteSurPlaceRepository;
import bf.laterrasse.nks.repository.DuoRepository;
import bf.laterrasse.nks.repository.QRCodeTicketRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Vote public sur place — cinématique anti-fraude (cf. échanges des 13/09/2026) :
 *
 * 1) Une HOTESSE en salle (plus de point de caisse unique ni d'agent d'accueil séparé pour
 *    ce flux) scanne le billet du client et confirme sa consommation en une seule action,
 *    à chaque service, où qu'elle se trouve — plusieurs hôtesses peuvent le faire en
 *    parallèle depuis leur propre téléphone, ce qui élimine le goulot d'étranglement d'un
 *    bar unique. Sous le capot, {@link #validerConsommation} réutilise
 *    {@link ScanService#scanner} pour le scan (anti-double-scan §14.6, verrou pessimiste sur
 *    le QR code, compteur d'entrées inchangé) : si le billet n'était pas encore scanné, il
 *    l'est à cet instant ; s'il l'était déjà (service suivant de la même soirée), l'appel est
 *    sans effet — dans les deux cas on obtient un billet garanti UTILISE avant de continuer.
 * 2) Une fois le billet garanti UTILISE, un DroitVoteSurPlace(DISPONIBLE) est créé pour ce
 *    billet. Contrainte UNIQUE sur ticket_id : impossible d'en émettre deux pour le même
 *    billet, même en rejouant l'opération à chaque service suivant (l'hôtesse reçoit
 *    simplement un message « déjà activé »).
 * 3) Le client exprime ce droit une seule fois via /vote-sur-place (public, aucune
 *    authentification requise — seule la connaissance du qrUuid, déjà le modèle de
 *    confiance du QR billet lui-même, y donne accès). Verrou pessimiste sur la ligne du
 *    droit de vote pendant la validation, pour empêcher deux tentatives concurrentes.
 *
 * Résultat : 1 billet scanné (à l'entrée ou par l'hôtesse qui sert la 1ère consommation) +
 * 1 consommation validée = au plus 1 vote sur place pour cette soirée, indépendamment du
 * nombre de numéros de téléphone détenus par la personne.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoteSurPlaceService {

    private final QRCodeTicketRepository qrCodeTicketRepository;
    private final DroitVoteSurPlaceRepository droitVoteSurPlaceRepository;
    private final SoireeEventRepository soireeEventRepository;
    private final CandidatRepository candidatRepository;
    private final VoteRepository voteRepository;
    private final AffectationPouleRepository affectationPouleRepository;
    private final DuoRepository duoRepository;
    private final WhatsappGateway whatsappGateway;
    private final ScanService scanService;

    @Value("${nks.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Action unique de l'hôtesse : scanne le billet (s'il ne l'était pas déjà) ET active la
     * consommation, dans le même appel. `ipHotesse`/`deviceInfo` sont transmis tels quels à
     * {@link ScanService#scanner} pour conserver la même traçabilité qu'un scan classique.
     */
    @Transactional
    public DroitVoteResponse validerConsommation(UUID qrUuid, UUID soireeId, Utilisateur hotesse,
                                                  String ipHotesse, String deviceInfo) {
        ScanResponse scan = scanService.scanner(qrUuid, soireeId, hotesse, ipHotesse, deviceInfo);
        if (ResultatScan.INVALIDE.name().equals(scan.resultat())) {
            throw new ValidationMetierException(
                    "Billet invalide, introuvable, annulé ou n'appartenant pas à cette soirée");
        }
        // VALIDE (premier scan à l'instant) ou DEJA_UTILISE (déjà entré / service précédent) :
        // dans les deux cas, le billet est maintenant garanti UTILISE — on peut continuer.

        Ticket ticket = resoudreTicket(qrUuid, soireeId);

        if (droitVoteSurPlaceRepository.existsByTicketId(ticket.getId())) {
            throw new ConflitEtatException("Ce billet a déjà un droit de vote pour cette soirée");
        }

        SoireeEvent soiree = ticket.getSoiree();
        if (!soiree.isVoteSurPlaceActif()) {
            throw new ValidationMetierException("Le vote sur place n'est pas (ou plus) actif pour cette soirée");
        }

        DroitVoteSurPlace droit = DroitVoteSurPlace.builder()
                .ticket(ticket)
                .soiree(soiree)
                .caissier(hotesse)
                .statut(StatutDroitVote.DISPONIBLE)
                .build();
        droit = droitVoteSurPlaceRepository.save(droit);

        envoyerLienWhatsapp(droit, ticket);

        return DroitVoteResponse.from(droit, ticket.getNomSpectateur(), List.of());
    }

    /**
     * Envoi best-effort — ferme la faille signalée le 13/09/2026 : le lien part directement
     * au numéro déjà associé à la réservation du billet, jamais affiché/relayé par le
     * caissier, ce qui rend son interception par un tiers bien plus difficile qu'un lien
     * montré à l'écran de la caisse. Un échec d'envoi (gateway simulée, réseau, numéro
     * invalide) ne doit jamais faire échouer la validation de la consommation elle-même.
     */
    private void envoyerLienWhatsapp(DroitVoteSurPlace droit, Ticket ticket) {
        try {
            String lien = frontendBaseUrl + "/vote-sur-place/" + droit.getSoiree().getId() + "/"
                    + qrCodeTicketRepository.findByTicketId(ticket.getId()).map(q -> q.getCodeUuid().toString()).orElse("");
            String message = "NKS : ta consommation est validée ! Vote pour ton candidat préféré via ce lien "
                    + "personnel (valable une seule fois) : " + lien;
            whatsappGateway.envoyer(SmsGateway.normaliserTelephone(ticket.getTelephoneSpectateur()),
                    "karaoke_info", List.of(message));
            droit.setLienWhatsappEnvoye(true);
            droitVoteSurPlaceRepository.save(droit);
        } catch (Exception e) {
            log.warn("Échec envoi WhatsApp du lien de vote sur place pour le ticket {} : {}",
                    ticket.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public DroitVoteResponse consulterDroit(UUID qrUuid, UUID soireeId) {
        Ticket ticket = resoudreTicket(qrUuid, soireeId);
        DroitVoteSurPlace droit = droitVoteSurPlaceRepository.findByTicketId(ticket.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune consommation validée pour ce billet — demandez au bar de valider votre commande"));

        List<Candidat> candidats = droit.getStatut() == StatutDroitVote.DISPONIBLE
                ? candidatsDeLaSoiree(soireeId)
                : List.of();
        return DroitVoteResponse.from(droit, ticket.getNomSpectateur(), candidats);
    }

    /**
     * `telephoneVotant`/`positionLatitude`/`positionLongitude`/`positionPrecisionM` sont
     * purement déclaratifs (jamais vérifiés ni requis) — conservés uniquement pour permettre
     * de recouper a posteriori un vote contesté avec le téléphone réel du billet
     * (ticket.telephoneSpectateur) ou avec la position du lieu de la soirée. Voir la Javadoc
     * de la classe pour le détail de la cinématique anti-fraude.
     */
    @Transactional
    public DroitVoteResponse voter(UUID qrUuid, UUID soireeId, UUID candidatId, String telephoneVotant,
                                    BigDecimal positionLatitude, BigDecimal positionLongitude,
                                    BigDecimal positionPrecisionM) {
        Ticket ticket = resoudreTicket(qrUuid, soireeId);
        DroitVoteSurPlace droit = droitVoteSurPlaceRepository.findByTicketIdForUpdate(ticket.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune consommation validée pour ce billet — demandez au bar de valider votre commande"));

        if (droit.getStatut() == StatutDroitVote.UTILISE) {
            throw new ConflitEtatException("Ce billet a déjà voté pour cette soirée");
        }

        Candidat candidat = candidatRepository.findById(candidatId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));

        boolean appartientALaSoiree = candidatsDeLaSoiree(soireeId).stream()
                .anyMatch(c -> c.getId().equals(candidatId));
        if (!appartientALaSoiree) {
            throw new ValidationMetierException("Ce candidat ne se produit pas lors de cette soirée");
        }

        Vote vote = Vote.builder()
                .candidat(candidat)
                .phase(ticket.getSoiree().getPhase())
                .typeVote(TypeVote.PUBLIC_SUR_PLACE)
                .nombreVoix(1)
                .sourceTelephone(ticket.getTelephoneSpectateur())
                .dateVote(Instant.now())
                .build();
        voteRepository.save(vote);

        droit.setStatut(StatutDroitVote.UTILISE);
        droit.setCandidat(candidat);
        droit.setDateVote(Instant.now());
        droit.setTelephoneVotant(telephoneVotant);
        droit.setPositionLatitude(positionLatitude);
        droit.setPositionLongitude(positionLongitude);
        droit.setPositionPrecisionM(positionPrecisionM);
        droit = droitVoteSurPlaceRepository.save(droit);

        if (telephoneVotant != null && !telephoneVotant.isBlank()
                && !SmsGateway.normaliserTelephone(telephoneVotant).equals(SmsGateway.normaliserTelephone(ticket.getTelephoneSpectateur()))) {
            log.warn("Vote sur place : téléphone saisi ({}) différent du téléphone du billet ({}) — ticket {}, à recouper en cas de contestation",
                    telephoneVotant, ticket.getTelephoneSpectateur(), ticket.getId());
        }

        return DroitVoteResponse.from(droit, ticket.getNomSpectateur(), List.of());
    }

    private Ticket resoudreTicket(UUID qrUuid, UUID soireeId) {
        soireeEventRepository.findById(soireeId)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));

        QRCodeTicket qr = qrCodeTicketRepository.findByCodeUuid(qrUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Billet introuvable"));
        Ticket ticket = qr.getTicket();
        if (!ticket.getSoiree().getId().equals(soireeId)) {
            throw new ValidationMetierException("Ce billet n'appartient pas à cette soirée");
        }
        return ticket;
    }

    /** Même résolution que JuryController.candidatsANoter — candidats affectés à la soirée via poule ou duo. */
    private List<Candidat> candidatsDeLaSoiree(UUID soireeId) {
        List<AffectationPoule> viaPoule = affectationPouleRepository.findByPouleSoireeId(soireeId);
        List<Duo> viaDuo = duoRepository.findBySoireeId(soireeId);

        Set<Candidat> candidats = Stream.concat(
                viaPoule.stream().map(AffectationPoule::getCandidat),
                viaDuo.stream().flatMap(d -> Stream.of(d.getCandidat1(), d.getCandidat2()))
        ).collect(java.util.stream.Collectors.toSet());
        return candidats.stream().toList();
    }
}
