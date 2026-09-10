package bf.laterrasse.nks.dto.admin;

import java.util.Map;

public record DashboardOrganisateurResponse(
        long candidatsTotal,
        long candidatsValides,
        long candidatsEnAttente,
        long candidatsEnAttentePaiement,
        long candidatsRejetes,
        Map<String, Long> votesTotauxParPhase,
        double tauxRemplissageMoyenSoirees
) {
}
