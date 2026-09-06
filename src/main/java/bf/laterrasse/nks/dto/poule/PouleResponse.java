package bf.laterrasse.nks.dto.poule;

import bf.laterrasse.nks.domain.Poule;

import java.time.Instant;
import java.util.UUID;

public record PouleResponse(
        UUID id,
        UUID phaseId,
        String nom,
        UUID soireeId,
        Instant dateCreation
) {
    public static PouleResponse from(Poule p) {
        return new PouleResponse(
                p.getId(),
                p.getPhase().getId(),
                p.getNom(),
                p.getSoiree() != null ? p.getSoiree().getId() : null,
                p.getDateCreation()
        );
    }
}
