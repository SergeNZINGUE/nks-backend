package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Jury;
import bf.laterrasse.nks.domain.Role;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.RoleName;
import bf.laterrasse.nks.domain.enums.Enums.StatutJury;
import bf.laterrasse.nks.domain.enums.Enums.StatutUtilisateur;
import bf.laterrasse.nks.dto.admin.CreerJuryRequest;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.EditionRepository;
import bf.laterrasse.nks.repository.JuryRepository;
import bf.laterrasse.nks.repository.RoleRepository;
import bf.laterrasse.nks.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** Gestion des comptes jury par l'admin (§13.15). */
@Service
@RequiredArgsConstructor
public class JuryAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JuryRepository juryRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final EditionRepository editionRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Transactional
    public Jury creer(CreerJuryRequest request) {
        Edition edition = editionRepository.findById(request.editionId())
                .orElseThrow(() -> new ResourceNotFoundException("Édition introuvable"));
        Role roleJury = roleRepository.findByNom(RoleName.JURY)
                .orElseThrow(() -> new IllegalStateException("Rôle JURY absent en base"));

        String motDePasseTemp = genererMotDePasseTemporaire();
        Utilisateur utilisateur = Utilisateur.builder()
                .email(request.email())
                .telephone(request.telephone())
                .motDePasseHash(passwordEncoder.encode(motDePasseTemp))
                .prenom(request.prenom())
                .nom(request.nom())
                .statut(StatutUtilisateur.ACTIF)
                .consentementRgpd(true)
                .dateConsentement(Instant.now())
                .build();
        utilisateur.getRoles().add(roleJury);
        utilisateur = utilisateurRepository.save(utilisateur);

        Jury jury = Jury.builder()
                .utilisateur(utilisateur)
                .edition(edition)
                .prenom(request.prenom())
                .nom(request.nom())
                .specialite(request.specialite())
                .bioPublique(request.bioPublique())
                .statut(StatutJury.ACTIF)
                .build();
        jury = juryRepository.save(jury);

        notificationService.envoyerSmsEtEmail(utilisateur, utilisateur.getTelephone(), utilisateur.getEmail(),
                bf.laterrasse.nks.domain.enums.Enums.TypeNotification.CONVOCATION,
                "NKS : vous avez été nommé(e) membre du jury. Mot de passe temporaire : " + motDePasseTemp,
                "NKS — Bienvenue au jury",
                "<p>Vous avez été nommé(e) membre du jury NKS.</p><p>Identifiant : " + utilisateur.getEmail()
                        + "<br/>Mot de passe temporaire : <strong>" + motDePasseTemp + "</strong></p>"
                        + "<p>Merci de le modifier dès votre première connexion.</p>");

        return jury;
    }

    @Transactional
    public void desactiver(UUID juryId) {
        Jury jury = juryRepository.findById(juryId)
                .orElseThrow(() -> new ResourceNotFoundException("Membre du jury introuvable"));
        jury.setStatut(StatutJury.INACTIF);
        juryRepository.save(jury);
    }

    private String genererMotDePasseTemporaire() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
