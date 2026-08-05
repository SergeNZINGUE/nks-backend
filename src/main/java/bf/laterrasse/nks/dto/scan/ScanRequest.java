package bf.laterrasse.nks.dto.scan;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ScanRequest(@NotNull UUID qrUuid, @NotNull UUID soireeId) {
}
