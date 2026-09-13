package bf.laterrasse.nks.dto.votesurplace;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CaisseValiderRequest(@NotNull UUID qrUuid, @NotNull UUID soireeId) {
}
