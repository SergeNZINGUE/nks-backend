package bf.laterrasse.nks.dto.poule;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.dto.candidat.CandidatPublicResponse;

import java.util.UUID;

public record AffectationPouleResponse(
        UUID id,
        CandidatPublicResponse candidat,
        UUID pouleId,
        Short ordrePassage,
        String chansonImposee
) {
    public static AffectationPouleResponse from(AffectationPoule a) {
        return new AffectationPouleResponse(
                a.getId(),
                CandidatPublicResponse.from(a.getCandidat()),
                a.getPoule().getId(),
                a.getOrdrePassage(),
                a.getChansonImposee()
        );
    }
}
