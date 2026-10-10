package bf.laterrasse.nks.dto.poule;

import bf.laterrasse.nks.domain.Duo;

import java.util.UUID;

public record DuoResponse(
        UUID id,
        UUID phaseId,
        UUID soireeId,
        UUID candidat1Id,
        String candidat1Code,
        String candidat1Prenom,
        String candidat1Nom,
        UUID candidat2Id,
        String candidat2Code,
        String candidat2Prenom,
        String candidat2Nom,
        String chansonCommune,
        Short ordrePassage
) {
    public static DuoResponse from(Duo d) {
        return new DuoResponse(
                d.getId(),
                d.getPhase().getId(),
                d.getSoiree() != null ? d.getSoiree().getId() : null,
                d.getCandidat1().getId(),
                d.getCandidat1().getCodeCandidat(),
                d.getCandidat1().getUtilisateur().getPrenom(),
                d.getCandidat1().getUtilisateur().getNom(),
                d.getCandidat2().getId(),
                d.getCandidat2().getCodeCandidat(),
                d.getCandidat2().getUtilisateur().getPrenom(),
                d.getCandidat2().getUtilisateur().getNom(),
                d.getChansonCommune(),
                d.getOrdrePassage()
        );
    }
}