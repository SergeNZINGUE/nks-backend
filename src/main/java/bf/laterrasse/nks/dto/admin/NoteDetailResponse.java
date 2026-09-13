package bf.laterrasse.nks.dto.admin;

import java.math.BigDecimal;
import java.util.UUID;

/** Une note d'un juré pour un critère précis — ligne de détail de la grille de délibération. */
public record NoteDetailResponse(
        UUID critereId,
        String critereNom,
        BigDecimal valeur
) {
}
