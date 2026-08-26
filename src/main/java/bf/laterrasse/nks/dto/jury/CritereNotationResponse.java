package bf.laterrasse.nks.dto.jury;

import bf.laterrasse.nks.domain.CritereNotation;

import java.math.BigDecimal;
import java.util.UUID;

public record CritereNotationResponse(
        UUID id,
        String nom,
        BigDecimal noteMin,
        BigDecimal noteMax,
        Short ordre
) {
    public static CritereNotationResponse from(CritereNotation c) {
        return new CritereNotationResponse(c.getId(), c.getNom(), c.getNoteMin(), c.getNoteMax(), c.getOrdre());
    }
}
