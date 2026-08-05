package bf.laterrasse.nks.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepechageRequest(
        @NotBlank @Size(min = 50, message = "Le motif de repêchage doit contenir au moins 50 caractères (RM-43)") String motif
) {
}
