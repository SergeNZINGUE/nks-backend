package bf.laterrasse.nks.dto.moment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motif obligatoire — jamais un rejet en un clic ; ce texte est repris tel quel dans la notification in-app envoyée au candidat. */
public record RejeterMomentRequest(
        @NotBlank @Size(max = 500) String motif
) {
}
