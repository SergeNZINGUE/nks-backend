package bf.laterrasse.nks.dto.admin;

import java.math.BigDecimal;
import java.util.Map;

public record DashboardResponse(
        long candidatsTotal,
        long candidatsValides,
        long candidatsEnAttente,
        long candidatsEnAttentePaiement,
        long candidatsRejetes,
        Map<String, Long> votesTotauxParPhase,
        BigDecimal revenusInscriptions,
        BigDecimal revenusVotes,
        BigDecimal revenusBillets,
        double tauxRemplissageMoyenSoirees
) {
}
