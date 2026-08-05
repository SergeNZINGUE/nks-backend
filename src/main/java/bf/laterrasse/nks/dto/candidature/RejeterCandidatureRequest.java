package bf.laterrasse.nks.dto.candidature;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejeterCandidatureRequest(
        @NotBlank @Size(min = 10, message = "Le motif de rejet doit contenir au moins 10 caractères") String motifRejet
) {
}
