package bf.laterrasse.nks.dto.billetterie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpDemanderRequest(
        @NotBlank(message = "Le numéro de téléphone est obligatoire")
        @Pattern(regexp = "^\\+?[0-9\\s-]{8,20}$", message = "Format de numéro de téléphone invalide")
        String telephone
) {
}
