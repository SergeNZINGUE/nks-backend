package bf.laterrasse.nks.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AffecterPouleRequest(@NotEmpty List<UUID> candidatIds) {
}
