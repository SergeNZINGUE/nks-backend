package bf.laterrasse.nks.dto.admin;

import bf.laterrasse.nks.domain.CritereNotation;

import java.math.BigDecimal;
import java.util.UUID;

/** Vue admin d'un critère de notation — inclut `actif` et `editionId`, contrairement à CritereNotationResponse (vue jury). */
public record CritereNotationAdminResponse(
        UUID id,
        UUID editionId,
        String nom,
        BigDecimal noteMin,
        BigDecimal noteMax,
        Short ordre,
        boolean actif
) {
    public static CritereNotationAdminResponse from(CritereNotation c) {
        return new CritereNotationAdminResponse(
                c.getId(), c.getEdition().getId(), c.getNom(), c.getNoteMin(), c.getNoteMax(), c.getOrdre(), c.isActif());
    }
}
