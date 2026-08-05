package bf.laterrasse.nks.security;

import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Le filtre JWT place l'UUID utilisateur comme "principal" de l'Authentication
 * (cf. JwtAuthenticationFilter). Ce composant recharge l'entité complète à la demande,
 * pour les services qui ont besoin de l'acteur (traçabilité AuditLog, notifications...).
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UtilisateurRepository utilisateurRepository;

    public UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        throw new AccesRefuseException("Utilisateur non authentifié");
    }

    public Utilisateur getCurrentUser() {
        return utilisateurRepository.findById(getCurrentUserId())
                .orElseThrow(() -> new AccesRefuseException("Utilisateur authentifié introuvable en base"));
    }
}
