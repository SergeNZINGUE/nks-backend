package bf.laterrasse.nks.dto.sms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SmsBulkRequest(
        @NotBlank(message = "Le message est obligatoire")
        @Size(max = 160, message = "Le message ne peut pas dépasser 160 caractères (1 segment SMS)")
        String message
) {}
