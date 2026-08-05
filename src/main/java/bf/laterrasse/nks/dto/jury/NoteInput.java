package bf.laterrasse.nks.dto.jury;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record NoteInput(@NotNull UUID critereId, @NotNull BigDecimal valeur) {
}
