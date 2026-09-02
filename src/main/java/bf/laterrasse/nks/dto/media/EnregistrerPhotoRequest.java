package bf.laterrasse.nks.dto.media;

import bf.laterrasse.nks.domain.enums.Enums.TypeMedia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EnregistrerPhotoRequest(
        @NotNull TypeMedia type,
        @NotBlank String publicId,
        @NotBlank String url,
        @Positive long tailleOctets
) {
}
