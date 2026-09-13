package bf.laterrasse.nks.dto.admin;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Corps de PUT /admin/jury/{id}/soirees — remplace intégralement l'ensemble des soirées
 * affectées à ce juré (pas d'ajout incrémental : la sélection envoyée devient la sélection
 * complète, comme un multi-select côté admin).
 */
public record AffecterSoireesJuryRequest(
        @NotNull Set<UUID> soireeIds
) {
}
