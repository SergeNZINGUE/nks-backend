package bf.laterrasse.nks.dto.admin;

import bf.laterrasse.nks.domain.Utilisateur;

import java.util.List;
import java.util.UUID;

public record UtilisateurAdminResponse(
        UUID id,
        String prenom,
        String nom,
        String email,
        String telephone,
        List<String> roles,
        String statut
) {
    public static UtilisateurAdminResponse from(Utilisateur u) {
        return new UtilisateurAdminResponse(
                u.getId(),
                u.getPrenom(),
                u.getNom(),
                u.getEmail(),
                u.getTelephone(),
                u.getRoles().stream().map(r -> r.getNom().name()).toList(),
                u.getStatut().name()
        );
    }
}
