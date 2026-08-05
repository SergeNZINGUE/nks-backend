package bf.laterrasse.nks.dto.video;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record UploaderVideoRequest(
        @NotNull UUID phaseId,
        @NotBlank String urlVideo,
        @Positive int dureeSecondes,
        @Positive long tailleOctets,
        String titreChanson
) {
}
