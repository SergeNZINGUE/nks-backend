package bf.laterrasse.nks.dto.titre;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreerTitreImposeRequest(@NotNull UUID phaseId, @NotBlank String titre, Short ordre) {
}
