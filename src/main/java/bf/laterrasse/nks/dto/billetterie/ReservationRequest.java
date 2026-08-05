package bf.laterrasse.nks.dto.billetterie;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record ReservationRequest(
        @NotNull UUID soireeId,
        @NotNull UUID categorieId,
        @Min(1) int nbPlaces,
        @NotBlank String nomReservant,
        @NotBlank String telephoneReservant,
        @Email String emailReservant
) {
}
