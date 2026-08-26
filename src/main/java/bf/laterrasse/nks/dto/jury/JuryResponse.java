package bf.laterrasse.nks.dto.jury;

import bf.laterrasse.nks.domain.Jury;

import java.util.UUID;

public record JuryResponse(
        UUID id,
        String prenom,
        String nom,
        String specialite,
        String bioPublique,
        String statut,
        UUID editionId,
        UUID utilisateurId
) {
    public static JuryResponse from(Jury j) {
        return new JuryResponse(
                j.getId(),
                j.getPrenom(),
                j.getNom(),
                j.getSpecialite(),
                j.getBioPublique(),
                j.getStatut().name(),
                j.getEdition().getId(),
                j.getUtilisateur().getId());
    }
}
