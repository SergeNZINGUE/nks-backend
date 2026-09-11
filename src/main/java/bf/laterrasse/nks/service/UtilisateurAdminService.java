package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Role;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.RoleName;
import bf.laterrasse.nks.domain.enums.Enums.StatutUtilisateur;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.dto.admin.CreerUtilisateurAdminRequest;
import bf.laterrasse.nks.dto.admin.UtilisateurAdminResponse;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.RoleRepository;
import bf.laterrasse.nks.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UtilisateurAdminService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<RoleName> ROLES_AUTORISES = EnumSet.of(RoleName.ADMIN, RoleName.SUPER_ADMIN, RoleName.AGENT_ACCUEIL, RoleName.ORGANISATEUR);

    private final UtilisateurRepository utilisateurRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Transactional
    public UtilisateurAdminResponse creer(CreerUtilisateurAdminRequest request) {
        if (!ROLES_AUTORISES.contains(request.role())) {
            throw new ValidationMetierException("Rôle non autorisé : " + request.role());
        }
        if (utilisateurRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflitEtatException("Email déjà utilisé");
        }

        Role role = roleRepository.findByNom(request.role())
                .orElseThrow(() -> new IllegalStateException("Rôle " + request.role() + " absent en base"));

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
        utilisateur.getRoles().add(role);
        utilisateur = utilisateurRepository.save(utilisateur);

        notificationService.envoyerSmsEtEmail(utilisateur, utilisateur.getTelephone(), utilisateur.getEmail(),
                TypeNotification.CONVOCATION,
                "NKS : votre compte a été créé. Identifiant : " + utilisateur.getEmail()
                        + " — Mot de passe temporaire : " + motDePasseTemp,
                "NKS — Bienvenue sur la plateforme",
                "<p>Votre compte NKS a été créé avec le rôle <strong>" + request.role().name() + "</strong>.</p>"
                        + "<p>Identifiant : " + utilisateur.getEmail()
                        + "<br/>Mot de passe temporaire : <strong>" + motDePasseTemp + "</strong></p>"
                        + "<p>Merci de le modifier dès votre première connexion.</p>");

        return UtilisateurAdminResponse.from(utilisateur);
    }

    @Transactional
    public void reinitialiserMotDePasse(UUID utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        boolean estCandidatUniquement = utilisateur.getRoles().stream()
                .allMatch(r -> r.getNom() == RoleName.CANDIDAT);
        if (estCandidatUniquement) {
            throw new ValidationMetierException("La réinitialisation de mot de passe des candidats n'est pas permise ici");
        }

        String motDePasseTemp = genererMotDePasseTemporaire();
        utilisateur.setMotDePasseHash(passwordEncoder.encode(motDePasseTemp));
        utilisateurRepository.save(utilisateur);

        notificationService.envoyerSmsEtEmail(utilisateur, utilisateur.getTelephone(), utilisateur.getEmail(),
                TypeNotification.CONVOCATION,
                "NKS : votre mot de passe a été réinitialisé. Nouveau mot de passe temporaire : " + motDePasseTemp,
                "NKS — Réinitialisation de mot de passe",
                "<p>Votre mot de passe a été réinitialisé par un administrateur.</p>"
                        + "<p>Nouveau mot de passe temporaire : <strong>" + motDePasseTemp + "</strong></p>"
                        + "<p>Merci de le modifier dès votre prochaine connexion.</p>");
    }

    public List<UtilisateurAdminResponse> listerAdmins() {
        return utilisateurRepository.findAll().stream()
                .filter(u -> u.getRoles().stream().anyMatch(r -> ROLES_AUTORISES.contains(r.getNom())))
                .map(UtilisateurAdminResponse::from)
                .toList();
    }

    private String genererMotDePasseTemporaire() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
