package bf.laterrasse.nks.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Corps de POST /admin/criteres-notation. */
public record CreerCritereNotationRequest(
        @NotNull UUID editionId,
        @NotBlank String nom,
        BigDecimal noteMin,
        @NotNull BigDecimal noteMax,
        @NotNull Short ordre
) {
}
