package bf.laterrasse.nks.dto.billetterie;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/** Corps de {@code POST /admin/billetterie/tickets-gratuits} (mêmes noms de champs que l'ancien Map). */
public record TicketsGratuitsRequest(
        @NotNull UUID soireeId,
        @NotNull UUID categorieId,
        @NotBlank @Size(max = 150) String nom,
        @NotBlank @Size(max = 30) String telephone,
        @Min(1) @Max(50) int nbPlaces,
        @NotNull @Size(min = 1, max = 50) List<@Valid BeneficiaireBilletRequest> beneficiaires
) {
}
