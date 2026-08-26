package bf.laterrasse.nks.dto.classement;

import bf.laterrasse.nks.domain.ResultatPhase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResultatPhaseResponse(
        UUID id,
        UUID candidatId,
        String codeCandidat,
        UUID phaseId,
        String nomPhase,
        BigDecimal pointsVotesEnLigne,
        BigDecimal pointsPublicSurPlace,
        BigDecimal pointsJury,
        BigDecimal totalPoints,
        Integer rang,
        String statutQualification,
        Instant dateCalcul
) {
    public static ResultatPhaseResponse from(ResultatPhase r) {
        return new ResultatPhaseResponse(
                r.getId(),
                r.getCandidat().getId(),
                r.getCandidat().getCodeCandidat(),
                r.getPhase().getId(),
                r.getPhase().getNom().name(),
                r.getPointsVotesEnLigne(),
                r.getPointsPublicSurPlace(),
                r.getPointsJury(),
                r.getTotalPoints(),
                r.getRang(),
                r.getStatutQualification().name(),
                r.getDateCalcul());
    }
}
