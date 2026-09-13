package bf.laterrasse.nks.dto.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Notes d'un juré donné pour un candidat, détaillées par critère, pour cette soirée. */
public record NoteParJuryResponse(
        UUID juryId,
        String juryNomComplet,
        List<NoteDetailResponse> details,
        BigDecimal totalJury
) {
}
