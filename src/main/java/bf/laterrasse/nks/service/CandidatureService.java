package bf.laterrasse.nks.service;

import bf.laterrasse.nks.config.MediaProperties;
import bf.laterrasse.nks.domain.*;
import bf.laterrasse.nks.domain.enums.Enums.*;
import bf.laterrasse.nks.dto.candidature.CandidatureSubmitRequest;
import bf.laterrasse.nks.dto.candidature.CandidatureSubmitResponse;
import bf.laterrasse.nks.event.PaiementConfirmeEvent;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * WF-01 (candidature complète) et WF-02 (validation/rejet). Le module Paiement notifie
 * ce service via {@link PaiementConfirmeEvent} une fois le webhook LigdiCash confirmé
 * pour un paiement de type INSCRIPTION (découplage évitant une dépendance circulaire
 * entre CandidatureService et PaiementService — cf. ADR-07).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CandidatureService {

    private static final int AGE_MINIMUM = 18;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UtilisateurRepository utilisateurRepository;
    private final CandidatRepository candidatRepository;
    private final CandidatureRepository candidatureRepository;
    private final MediaRepository mediaRepository;
    private final VideoRepository videoRepository;
    private final EditionRepository editionRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final MediaProperties mediaProperties;

    @Transactional
    public CandidatureSubmitResponse soumettre(CandidatureSubmitRequest req) {
        Edition edition = editionRepository.findById(req.editionId())
                .orElseThrow(() -> new ResourceNotFoundException("Édition introuvable"));

        validerAge(req.dateNaissance());
        validerMotivation(req.motivation());
        validerVideo(req.dureeVideoSecondes(), req.tailleVideoOctets());
        validerPhoto(req.taillePhotoOctets());
        validerUrlMedia(req.urlPhoto());
        validerUrlMedia(req.urlVideo());

        Utilisateur utilisateurExistant = utilisateurRepository
                .findByTelephoneAndDateSuppressionIsNull(req.telephone())
                .orElse(null);

        if (utilisateurExistant != null) {
            boolean dejaCandidatSurEdition = candidatRepository.findByUtilisateurId(utilisateurExistant.getId())
                    .map(c -> candidatureRepository.existsByCandidatIdAndEditionIdAndStatutIn(
                            c.getId(), edition.getId(),
                            List.of(StatutCandidature.EN_ATTENTE, StatutCandidature.EN_ATTENTE_PAIEMENT, StatutCandidature.ACTIVE)))
                    .orElse(false);
            if (dejaCandidatSurEdition) {
                throw new ConflitEtatException("Une candidature existe déjà pour ce numéro sur cette édition (RM-08)");
            }
        }

        String motDePasseTemp = null;
        Utilisateur utilisateur;
        if (utilisateurExistant != null) {
            utilisateur = utilisateurExistant;
        } else {
            motDePasseTemp = genererMotDePasseTemporaire();
            utilisateur = creerUtilisateurCandidat(req, motDePasseTemp);
        }

        String codeCandidat = genererCodeCandidat(edition.getId());

        Candidat candidat = Candidat.builder()
                .utilisateur(utilisateur)
                .edition(edition)
                .codeCandidat(codeCandidat)
                .dateNaissance(req.dateNaissance())
                .ageALInscription((short) Period.between(req.dateNaissance(), LocalDate.now()).getYears())
                .chansonPreselection(req.chansonPreselection())
                .statutProfil(StatutProfilCandidat.EN_ATTENTE)
                .build();
        candidat = candidatRepository.save(candidat);

        Candidature candidature = Candidature.builder()
                .candidat(candidat)
                .edition(edition)
                .statut(StatutCandidature.EN_ATTENTE)
                .motivation(req.motivation())
                .captureFbTiktokUrl(req.urlCaptureSocial())
                .build();
        candidature = candidatureRepository.save(candidature);

        mediaRepository.save(Media.builder()
                .candidat(candidat)
                .type(TypeMedia.PHOTO_PROFIL)
                .urlStockage(req.urlPhoto())
                .tailleOctets(req.taillePhotoOctets())
                .format(req.formatPhoto().toUpperCase())
                .statut(StatutMedia.VALIDE)
                .build());

        videoRepository.save(Video.builder()
                .candidat(candidat)
                .phase(null) // vidéo de présélection, cf. §8.2 Vidéo : phase_id NULL si présélection
                .urlStockageOriginale(req.urlVideo())
                .urlStreaming(req.urlVideo())
                .dureeSecondes(req.dureeVideoSecondes())
                .tailleOctets(req.tailleVideoOctets())
                .titreChanson(req.chansonPreselection())
                .statut(StatutVideo.DISPONIBLE)
                .build());

        String sms = "NKS : dossier reçu, code candidat " + codeCandidat
                + (motDePasseTemp != null ? ", mot de passe temporaire : " + motDePasseTemp : "")
                + ". Vous serez notifié après examen.";
        String emailCorps = "<p>Bonjour " + utilisateur.getPrenom() + ",</p>"
                + "<p>Votre dossier de candidature a bien été reçu. Votre code candidat est <strong>"
                + codeCandidat + "</strong>.</p>"
                + (motDePasseTemp != null
                        ? "<p>Votre mot de passe temporaire est : <strong>" + motDePasseTemp + "</strong>."
                                + " Changez-le dès votre première connexion.</p>"
                        : "")
                + "<p>L'équipe NKS l'examinera prochainement.</p>";

        notificationService.envoyerSmsEtEmail(utilisateur, utilisateur.getTelephone(), utilisateur.getEmail(),
                TypeNotification.CANDIDATURE_RECUE, sms, "NKS — Candidature reçue", emailCorps);

        return new CandidatureSubmitResponse(candidature.getId(), codeCandidat, candidature.getStatut().name());
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "CANDIDATURE_VALIDEE", entite = "Candidature")
    public Candidature valider(UUID candidatureId, Utilisateur admin) {
        Candidature candidature = getCandidatureEnAttente(candidatureId);
        candidature.setStatut(StatutCandidature.EN_ATTENTE_PAIEMENT);
        candidature.setAdmin(admin);
        candidature.setDateTraitementAdmin(Instant.now());
        candidature.setDateModification(Instant.now());
        candidatureRepository.save(candidature);

        try {
            Utilisateur candidat = candidature.getCandidat().getUtilisateur();
            notificationService.envoyerSmsEtEmail(candidat, candidat.getTelephone(), candidat.getEmail(),
                    TypeNotification.CANDIDATURE_VALIDEE,
                    "NKS : candidature acceptée ! Connectez-vous pour régler vos frais d'inscription.",
                    "NKS — Candidature acceptée",
                    "<p>Félicitations, votre candidature a été acceptée. Connectez-vous à votre espace pour"
                            + " procéder au paiement des frais d'inscription et activer votre profil.</p>");
        } catch (Exception e) {
            log.warn("Notifications non envoyées pour validation candidature {} : {}", candidatureId, e.getMessage());
        }

        return candidature;
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "CANDIDATURE_REJETEE", entite = "Candidature")
    public Candidature rejeter(UUID candidatureId, Utilisateur admin, String motifRejet) {
        Candidature candidature = getCandidatureEnAttente(candidatureId);
        candidature.setStatut(StatutCandidature.REJETEE);
        candidature.setMotifRejet(motifRejet);
        candidature.setAdmin(admin);
        candidature.setDateTraitementAdmin(Instant.now());
        candidature.setDateModification(Instant.now());
        candidatureRepository.save(candidature);

        try {
            Utilisateur candidat = candidature.getCandidat().getUtilisateur();

            notificationService.envoyerSmsEtEmail(candidat, candidat.getTelephone(), candidat.getEmail(),
                    TypeNotification.CANDIDATURE_REJETEE,
                    "NKS : votre candidature n'a pas été retenue. Motif : " + tronquer(motifRejet, 100),
                    "NKS — Candidature non retenue",
                    "<p>Nous vous remercions pour votre candidature. Elle n'a malheureusement pas été retenue.</p>"
                            + "<p><strong>Motif :</strong> " + motifRejet + "</p>");
        } catch (Exception e) {
            log.warn("Notifications non envoyées pour rejet candidature {} : {}", candidatureId, e.getMessage());
        }

        return candidature;
    }

    @EventListener
    public void onPaiementConfirme(PaiementConfirmeEvent event) {
        if (event.typePaiement() != TypePaiement.INSCRIPTION || event.utilisateurId() == null) {
            return;
        }
        candidatRepository.findByUtilisateurId(event.utilisateurId()).ifPresentOrElse(
                candidat -> candidatureRepository
                        .findByCandidatIdAndEditionId(candidat.getId(), candidat.getEdition().getId())
                        .ifPresentOrElse(
                                c -> activerApresPaiement(c.getId()),
                                () -> log.warn("Paiement INSCRIPTION confirmé mais aucune candidature EN_ATTENTE_PAIEMENT pour candidat {}", candidat.getId())),
                () -> log.warn("Paiement INSCRIPTION confirmé pour un utilisateur sans profil candidat : {}", event.utilisateurId()));
    }

    /** Appelé (directement ou via {@link #onPaiementConfirme}) une fois le paiement d'inscription confirmé. */
    @Transactional
    public void activerApresPaiement(UUID candidatureId) {
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidature introuvable"));
        if (candidature.getStatut() != StatutCandidature.EN_ATTENTE_PAIEMENT) {
            throw new ConflitEtatException("La candidature n'est pas en attente de paiement");
        }
        candidature.setStatut(StatutCandidature.ACTIVE);
        candidature.setDateModification(Instant.now());
        candidatureRepository.save(candidature);

        Candidat candidat = candidature.getCandidat();
        candidat.setStatutProfil(StatutProfilCandidat.ACTIF);
        candidat.setDateActivationProfil(Instant.now());
        candidatRepository.save(candidat);

        try {
            Utilisateur utilisateur = candidat.getUtilisateur();
            notificationService.envoyerSmsEtEmail(utilisateur, utilisateur.getTelephone(), utilisateur.getEmail(),
                    TypeNotification.PROFIL_ACTIVE,
                    "NKS : paiement confirmé, votre profil est désormais actif et visible publiquement !",
                    "NKS — Profil activé",
                    "<p>Votre paiement a été confirmé. Votre profil candidat est maintenant actif et visible"
                            + " sur la galerie publique NKS.</p>");
        } catch (Exception e) {
            log.warn("Notifications non envoyées pour activation profil candidature {} : {}", candidatureId, e.getMessage());
        }
    }

    private Candidature getCandidatureEnAttente(UUID candidatureId) {
        Candidature candidature = candidatureRepository.findById(candidatureId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidature introuvable"));
        if (candidature.getStatut() != StatutCandidature.EN_ATTENTE) {
            throw new ConflitEtatException("Cette candidature a déjà été traitée (statut : " + candidature.getStatut() + ")");
        }
        return candidature;
    }

    private Utilisateur creerUtilisateurCandidat(CandidatureSubmitRequest req, String motDePasseTemp) {
        Role roleCandidat = roleRepository.findByNom(RoleName.CANDIDAT)
                .orElseThrow(() -> new IllegalStateException("Rôle CANDIDAT absent en base — vérifier le seed V1__init_schema.sql"));

        Utilisateur utilisateur = Utilisateur.builder()
                .email(req.email())
                .telephone(req.telephone())
                .motDePasseHash(passwordEncoder.encode(motDePasseTemp))
                .prenom(req.prenom())
                .nom(req.nom())
                .statut(StatutUtilisateur.ACTIF)
                .consentementRgpd(true)
                .dateConsentement(Instant.now())
                .build();
        utilisateur.getRoles().add(roleCandidat);
        return utilisateurRepository.save(utilisateur);
    }

    /**
     * Génère le prochain code séquentiel (K01, K02...K10, K11...). Basé sur le nombre de
     * candidats déjà inscrits sur l'édition plutôt que sur un tri lexicographique du
     * dernier code (qui trierait "K10" avant "K2" en ordre alphabétique) — plus robuste
     * au-delà de 9 candidats.
     */
    private String genererCodeCandidat(UUID editionId) {
        long count = candidatRepository.countByEditionId(editionId);
        return String.format("K%02d", count + 1);
    }

    private void validerAge(LocalDate dateNaissance) {
        int age = Period.between(dateNaissance, LocalDate.now()).getYears();
        if (age < AGE_MINIMUM) {
            throw new ValidationMetierException("Âge minimum requis : " + AGE_MINIMUM + " ans (RM-01)");
        }
    }

    private void validerMotivation(String motivation) {
        if (motivation != null && motivation.trim().split("\\s+").length > 200) {
            throw new ValidationMetierException("La motivation est limitée à 200 mots (RM-06)");
        }
    }

    private void validerVideo(int dureeSecondes, long tailleOctets) {
        if (dureeSecondes < mediaProperties.getVideoMinDurationSeconds()
                || dureeSecondes > mediaProperties.getVideoMaxDurationSeconds()) {
            throw new ValidationMetierException("La vidéo de présélection doit durer entre "
                    + mediaProperties.getVideoMinDurationSeconds() + " et "
                    + mediaProperties.getVideoMaxDurationSeconds() + " secondes (RM-04)");
        }
        if (tailleOctets > mediaProperties.getMaxVideoSizeBytes()) {
            throw new ValidationMetierException("Vidéo trop lourde (RM-05, décision client : "
                    + (mediaProperties.getMaxVideoSizeBytes() / 1024 / 1024) + " Mo max)");
        }
    }

    private void validerPhoto(long tailleOctets) {
        if (tailleOctets > mediaProperties.getMaxPhotoSizeBytes()) {
            throw new ValidationMetierException("Photo trop lourde (RM-03, 5 Mo max)");
        }
    }

    private String genererMotDePasseTemporaire() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validerUrlMedia(String url) {
        String cloudName = mediaProperties.getCloudinary().getCloudName();
        // Les blob:// URLs sont un fallback frontend dev-only — jamais hébergées sur Cloudinary
        if (url != null && cloudName != null && !cloudName.isBlank() && url.startsWith("https://")) {
            String expectedPrefix = "https://res.cloudinary.com/" + cloudName + "/";
            if (!url.startsWith(expectedPrefix)) {
                throw new ValidationMetierException("URL média invalide : le fichier doit être hébergé sur Cloudinary");
            }
        }
    }

    private String tronquer(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
