package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import bf.laterrasse.nks.dto.candidat.CandidatPublicResponse;
import bf.laterrasse.nks.dto.candidat.MettreAJourProfilRequest;
import bf.laterrasse.nks.dto.classement.ResultatPhaseResponse;
import bf.laterrasse.nks.dto.titre.ChoisirTitreRequest;
import bf.laterrasse.nks.dto.titre.ChoixTitreResponse;
import bf.laterrasse.nks.dto.titre.MonChoixTitreResponse;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.ResultatPhaseRepository;
import bf.laterrasse.nks.repository.UtilisateurRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.ChoixTitreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** §13.5 — Galerie publique et profils candidats (US-12, US-15, US-16). */
@RestController
@RequestMapping("/candidats")
@RequiredArgsConstructor
public class CandidatController {

    private final CandidatRepository candidatRepository;
    private final ResultatPhaseRepository resultatPhaseRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ChoixTitreService choixTitreService;

    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<CandidatPublicResponse>> galerie(
            @RequestParam UUID editionId,
            @RequestParam(required = false) StatutProfilCandidat statutProfil,
            Pageable pageable) {
        StatutProfilCandidat filtre = statutProfil != null ? statutProfil : StatutProfilCandidat.ACTIF;
        Page<CandidatPublicResponse> page = candidatRepository
                .findByEditionIdAndStatutProfil(editionId, filtre, pageable)
                .map(CandidatPublicResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<CandidatPublicResponse> profil(@PathVariable UUID id) {
        return ResponseEntity.ok(CandidatPublicResponse.from(getCandidat(id)));
    }

    @GetMapping("/code/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<CandidatPublicResponse> parCode(@PathVariable String code, @RequestParam UUID editionId) {
        Candidat candidat = candidatRepository.findByEditionIdAndCodeCandidat(editionId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable pour le code " + code));
        return ResponseEntity.ok(CandidatPublicResponse.from(candidat));
    }

    @GetMapping("/{id}/scores")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ResultatPhaseResponse>> scores(@PathVariable UUID id) {
        getCandidat(id); // 404 si le candidat n'existe pas
        List<ResultatPhaseResponse> result = resultatPhaseRepository.findByCandidatIdEtResultatsPublies(id).stream()
                .map(ResultatPhaseResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * Édition admin du profil candidat. `biographie`/`chansonPreselection` restent
     * optionnels (édition partielle historique) ; `prenom`/`nom`/`email`/`telephone`
     * portent sur {@code Utilisateur} (pas {@code Candidat}) — cf. mapping utilisateur_id.
     * `email`/`telephone` sont soumis à une contrainte UNIQUE en base : vérifiés ici pour
     * renvoyer une erreur métier claire plutôt qu'une violation de contrainte brute.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<CandidatPublicResponse> mettreAJourAdmin(@PathVariable UUID id,
                                                                     @RequestBody java.util.Map<String, Object> body) {
        Candidat candidat = candidatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));
        if (body.get("biographie") instanceof String bio) candidat.setBiographie(bio);
        if (body.get("chansonPreselection") instanceof String chanson) candidat.setChansonPreselection(chanson);

        Utilisateur utilisateur = candidat.getUtilisateur();
        if (body.get("prenom") instanceof String prenom) {
            if (prenom.isBlank()) throw new ValidationMetierException("Le prénom ne peut pas être vide");
            utilisateur.setPrenom(prenom.trim());
        }
        if (body.get("nom") instanceof String nom) {
            if (nom.isBlank()) throw new ValidationMetierException("Le nom ne peut pas être vide");
            utilisateur.setNom(nom.trim());
        }
        if (body.get("email") instanceof String email) {
            String emailNormalise = email.trim();
            if (!EMAIL_PATTERN.matcher(emailNormalise).matches()) {
                throw new ValidationMetierException("Adresse e-mail invalide");
            }
            if (!emailNormalise.equalsIgnoreCase(utilisateur.getEmail())
                    && utilisateurRepository.existsByEmailIgnoreCase(emailNormalise)) {
                throw new ValidationMetierException("Cet e-mail est déjà utilisé par un autre compte");
            }
            utilisateur.setEmail(emailNormalise);
        }
        if (body.get("telephone") instanceof String telephone) {
            if (telephone.isBlank()) throw new ValidationMetierException("Le téléphone ne peut pas être vide");
            String telephoneNormalise = SmsGateway.normaliserTelephone(telephone.trim());
            if (!telephoneNormalise.equals(utilisateur.getTelephone())
                    && utilisateurRepository.existsByTelephone(telephoneNormalise)) {
                throw new ValidationMetierException("Ce téléphone est déjà utilisé par un autre compte");
            }
            utilisateur.setTelephone(telephoneNormalise);
        }
        utilisateurRepository.save(utilisateur);
        return ResponseEntity.ok(CandidatPublicResponse.from(candidatRepository.save(candidat)));
    }

    @PutMapping("/mon-profil")
    @PreAuthorize("hasRole('CANDIDAT')")
    @Transactional
    public ResponseEntity<CandidatPublicResponse> mettreAJourMonProfil(@Valid @RequestBody MettreAJourProfilRequest request) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        Candidat candidat = candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil candidat pour cet utilisateur"));
        if (request.biographie() != null) {
            candidat.setBiographie(request.biographie());
        }
        return ResponseEntity.ok(CandidatPublicResponse.from(candidatRepository.save(candidat)));
    }

    /**
     * Acceptation explicite du Recueil de consentement (règlement, données personnelles,
     * frais non remboursables, droit à l'image, clauses de litige). Contrôlé à chaque
     * connexion — cf. AuthService.calculerConsentementRequis / LoginResponse.consentementRequis.
     */
    @PostMapping("/mon-consentement")
    @PreAuthorize("hasRole('CANDIDAT')")
    @Transactional
    public ResponseEntity<Void> accepterConsentement() {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        Candidat candidat = candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil candidat pour cet utilisateur"));
        candidat.setConsentementRecueilAccepte(true);
        candidat.setDateConsentementRecueil(java.time.Instant.now());
        candidatRepository.save(candidat);
        return ResponseEntity.noContent().build();
    }

    /**
     * Titres imposés disponibles + choix déjà fait pour la soirée à venir du candidat
     * connecté (résolue automatiquement — cf. ChoixTitreService.resoudreSoireeActuelle).
     */
    @GetMapping("/mon-choix-titre")
    @PreAuthorize("hasRole('CANDIDAT')")
    @Transactional(readOnly = true)
    public ResponseEntity<MonChoixTitreResponse> monChoixTitre() {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(choixTitreService.monChoixTitre(utilisateurId));
    }

    @PostMapping("/mon-choix-titre")
    @PreAuthorize("hasRole('CANDIDAT')")
    @Transactional
    public ResponseEntity<ChoixTitreResponse> choisirTitre(@Valid @RequestBody ChoisirTitreRequest request) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(choixTitreService.choisir(utilisateurId, request));
    }

    private Candidat getCandidat(UUID id) {
        return candidatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));
    }
}
