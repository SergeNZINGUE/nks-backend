package bf.laterrasse.nks.dto.sms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class SmsRequest {
    @NotBlank(message = "Le numéro est obligatoire")
    @Pattern(
            regexp = "^\\+[1-9]\\d{7,14}$",
            message = "Le numéro doit être au format international E.164"
    )
    private String to;

    @NotBlank(message = "Le message est obligatoire")
    private String message;

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
