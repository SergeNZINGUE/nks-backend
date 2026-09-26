package bf.laterrasse.nks.service;

import bf.laterrasse.nks.config.MediaProperties;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.MomentEvenement;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.RoleName;
import bf.laterrasse.nks.domain.enums.Enums.StatutMedia;
import bf.laterrasse.nks.domain.enums.Enums.TypeMoment;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.dto.moment.CreerMomentAdminRequest;
import bf.laterrasse.nks.dto.moment.CreerMomentCandidatRequest;
import bf.laterrasse.nks.dto.moment.MomentEvenementResponse;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.MomentEvenementRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * "Moments de l'événement" — cf. MomentEvenement pour le pourquoi d'une table dédiée.
 * Deux chemins d'écriture bien distincts :
 *  - candidat (creerParCandidat) : file d'attente (EN_ATTENTE), un seul uploadeur, jamais
 *    de candidatsTagues/soiree/legende (réservés à l'admin, décision produit confirmée) ;
 *  - admin/organisateur (creerParAdmin) : publié immédiatement (VALIDE), candidatsTagues/
 *    soiree/legende disponibles, aucun uploadeur candidat (crédit "Équipe NKS" si aucun
 *    candidat coché côté frontend).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MomentEvenementService {

    private final MomentEvenementRepository momentRepository;
    private final CandidatRepository candidatRepository;
    private final SoireeEventRepository soireeEventRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final MediaProperties mediaProperties;
    private final NotificationService notificationService;

    public MomentEvenementResponse creerParCandidat(UUID utilisateurId, CreerMomentCandidatRequest request) {
        Candidat candidat = candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new AccesRefuseException("Aucun profil candidat pour cet utilisateur"));
        validerTaille(request.type(), request.tailleOctets());

        MomentEvenement moment = MomentEvenement.builder()
                .type(request.type())
                .urlStockage(request.url())
                .nomFichierOriginal(request.publicId())
                .tailleOctets(request.tailleOctets())
                .format(extraireFormat(request.publicId()))
                .candidatUploadeur(candidat)
                .statut(StatutMedia.EN_ATTENTE)
                .build();
        moment = momentRepository.save(moment);

        notifierAdminsNouveauMoment(candidat);
        return MomentEvenementResponse.from(moment);
    }

    /**
     * Un candidat ne peut retirer son propre envoi que tant qu'il est EN_ATTENTE — une
     * fois publié (VALIDE), c'est irréversible de son côté (décision produit confirmée) ;
     * un moment MASQUE (rejeté) reste consultable dans son historique, pas retirable non
     * plus (il a déjà quitté la file, le retirer n'aurait pas de sens).
     */
    public void retirerParCandidat(UUID utilisateurId, UUID momentId) {
        Candidat candidat = candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new AccesRefuseException("Aucun profil candidat pour cet utilisateur"));
        MomentEvenement moment = getOrThrow(momentId);
        if (moment.getCandidatUploadeur() == null || !moment.getCandidatUploadeur().getId().equals(candidat.getId())) {
            throw new AccesRefuseException("Ce moment ne t'appartient pas");
        }
        if (moment.getStatut() != StatutMedia.EN_ATTENTE) {
            throw new ValidationMetierException("Impossible de retirer un moment déjà traité (publié ou rejeté)");
        }
        momentRepository.delete(moment);
    }

    public void supprimerParAdmin(UUID id) {
        momentRepository.delete(getOrThrow(id));
    }

    public List<MomentEvenementResponse> listerPourCandidat(UUID utilisateurId) {
        Candidat candidat = candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new AccesRefuseException("Aucun profil candidat pour cet utilisateur"));
        return momentRepository.findByCandidatUploadeurIdOrderByDateUploadDesc(candidat.getId())
                .stream().map(MomentEvenementResponse::from).toList();
    }

    public MomentEvenementResponse creerParAdmin(UUID utilisateurId, CreerMomentAdminRequest request) {
        validerTaille(request.type(), request.tailleOctets());

        Utilisateur ajoutePar = utilisateurRepository.findById(utilisateurId).orElse(null);
        SoireeEvent soiree = request.soireeId() != null
                ? soireeEventRepository.findById(request.soireeId()).orElse(null)
                : null;
        Set<Candidat> tagues = request.candidatIds() == null || request.candidatIds().isEmpty()
                ? Set.of()
                : new HashSet<>(candidatRepository.findAllById(request.candidatIds()));

        MomentEvenement moment = MomentEvenement.builder()
                .type(request.type())
                .urlStockage(request.url())
                .nomFichierOriginal(request.publicId())
                .tailleOctets(request.tailleOctets())
                .format(extraireFormat(request.publicId()))
                .legende(request.legende())
                .soiree(soiree)
                .ajoutePar(ajoutePar)
                .candidatsTagues(tagues)
                .statut(StatutMedia.VALIDE)
                .dateModeration(Instant.now())
                .build();
        return MomentEvenementResponse.from(momentRepository.save(moment));
    }

    public List<MomentEvenementResponse> listerEnAttente() {
        return momentRepository.findByStatutOrderByDateUploadAsc(StatutMedia.EN_ATTENTE)
                .stream().map(MomentEvenementResponse::from).toList();
    }

    /** Alimente le badge de la sidebar admin/organisateur — jamais chargé depuis la liste complète. */
    public long compterEnAttente() {
        return momentRepository.countByStatut(StatutMedia.EN_ATTENTE);
    }

    public Page<MomentEvenementResponse> listerPublic(int page, int size) {
        return momentRepository
                .findByStatutOrderByDateUploadDesc(StatutMedia.VALIDE, PageRequest.of(page, size))
                .map(MomentEvenementResponse::from);
    }

    public MomentEvenementResponse valider(UUID id) {
        MomentEvenement moment = getOrThrow(id);
        moment.setStatut(StatutMedia.VALIDE);
        moment.setDateModeration(Instant.now());
        momentRepository.save(moment);
        notifierCandidatUploadeur(moment, TypeNotification.MOMENT_VALIDE,
                "Ta photo/vidéo a été validée et publiée dans \"Moments de l'événement\".");
        return MomentEvenementResponse.from(moment);
    }

    public MomentEvenementResponse rejeter(UUID id, String motif) {
        MomentEvenement moment = getOrThrow(id);
        moment.setStatut(StatutMedia.MASQUE);
        moment.setMotifRejet(motif);
        moment.setDateModeration(Instant.now());
        momentRepository.save(moment);
        notifierCandidatUploadeur(moment, TypeNotification.MOMENT_REJETE,
                "Ton envoi n'a pas été publié. Motif : " + motif);
        return MomentEvenementResponse.from(moment);
    }

    /** Réservé admin/organisateur (contrôlé côté controller) — jamais accessible à un candidat sur son propre moment. */
    public MomentEvenementResponse mettreALaUne(UUID id) {
        MomentEvenement moment = getOrThrow(id);
        if (moment.getStatut() != StatutMedia.VALIDE) {
            throw new ValidationMetierException("Seul un moment déjà publié peut être mis à la une");
        }
        moment.setEnVedette(true);
        momentRepository.save(moment);
        notifierCandidatUploadeur(moment, TypeNotification.MOMENT_A_LA_UNE,
                "Ton moment a été mis à la une par l'équipe NKS !");
        return MomentEvenementResponse.from(moment);
    }

    private MomentEvenement getOrThrow(UUID id) {
        return momentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Moment introuvable"));
    }

    private void validerTaille(TypeMoment type, long tailleOctets) {
        long max = type == TypeMoment.VIDEO
                ? mediaProperties.getMaxVideoSizeBytes()
                : mediaProperties.getMaxPhotoSizeBytes();
        if (tailleOctets > max) {
            throw new ValidationMetierException("Fichier trop lourd : maximum " + (max / 1024 / 1024) + " Mo");
        }
    }

    private String extraireFormat(String publicId) {
        if (publicId == null) {
            return "JPG";
        }
        int dot = publicId.lastIndexOf('.');
        return dot >= 0 && dot < publicId.length() - 1 ? publicId.substring(dot + 1).toUpperCase() : "JPG";
    }

    /** Rien à notifier pour un ajout admin sans uploadeur candidat (moment.candidatUploadeur == null). */
    private void notifierCandidatUploadeur(MomentEvenement moment, TypeNotification type, String message) {
        if (moment.getCandidatUploadeur() == null) {
            return;
        }
        notificationService.envoyerInApp(moment.getCandidatUploadeur().getUtilisateur(), type, message);
    }

    private void notifierAdminsNouveauMoment(Candidat candidat) {
        List<Utilisateur> destinataires = utilisateurRepository.findByRolesNomIn(
                List.of(RoleName.ADMIN, RoleName.SUPER_ADMIN, RoleName.ORGANISATEUR));
        String nomCandidat = candidat.getUtilisateur().getPrenom() + " " + candidat.getUtilisateur().getNom();
        for (Utilisateur admin : destinataires) {
            notificationService.envoyerInApp(admin, TypeNotification.MOMENT_A_MODERER,
                    "Nouveau moment à modérer — " + nomCandidat);
        }
    }
}
