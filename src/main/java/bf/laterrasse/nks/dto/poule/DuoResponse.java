package bf.laterrasse.nks.dto.poule;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Duo;

import java.util.UUID;

public record DuoResponse(
        UUID id,
        UUID phaseId,
        UUID soireeId,
        CandidatInfo candidat1,
        CandidatInfo candidat2,
        String chansonCommune,
        Short ordrePassage
) {
    public record CandidatInfo(
            UUID id,
            String codeCandidat,
            String prenom,
            String nom,
            String biographie,
            String chansonPreselection,
            String statutProfil
    ) {
        static CandidatInfo from(Candidat c) {
            return new CandidatInfo(
                    c.getId(),
                    c.getCodeCandidat(),
                    c.getUtilisateur().getPrenom(),
                    c.getUtilisateur().getNom(),
                    c.getBiographie(),
                    c.getChansonPreselection(),
                    c.getStatutProfil().name()
            );
        }
    }

    public static DuoResponse from(Duo d) {
        return new DuoResponse(
                d.getId(),
                d.getPhase().getId(),
                d.getSoiree() != null ? d.getSoiree().getId() : null,
                CandidatInfo.from(d.getCandidat1()),
                CandidatInfo.from(d.getCandidat2()),
                d.getChansonCommune(),
                d.getOrdrePassage()
        );
    }
}