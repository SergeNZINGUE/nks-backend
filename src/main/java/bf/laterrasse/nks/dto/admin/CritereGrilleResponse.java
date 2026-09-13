package bf.laterrasse.nks.dto.admin;

import bf.laterrasse.nks.domain.CritereNotation;

import java.math.BigDecimal;
import java.util.UUID;

/** En-tête de colonne de la grille de délibération — un par critère actif de l'édition. */
public record CritereGrilleResponse(
        UUID id,
        String nom,
        BigDecimal noteMax,
        Short ordre
) {
    public static CritereGrilleResponse from(CritereNotation c) {
        return new CritereGrilleResponse(c.getId(), c.getNom(), c.getNoteMax(), c.getOrdre());
    }
}
