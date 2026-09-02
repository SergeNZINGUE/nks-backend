package bf.laterrasse.nks.dto.billetterie;

import jakarta.validation.constraints.NotBlank;

public record AnnulerReservationRequest(
        @NotBlank(message = "Le numéro de téléphone est obligatoire") String telephone
) {
}
