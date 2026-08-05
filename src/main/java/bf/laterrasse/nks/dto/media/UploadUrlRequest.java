package bf.laterrasse.nks.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record UploadUrlRequest(
        @NotBlank String type,       // PHOTO_PROFIL | CAPTURE_SOCIAL | VIDEO_PRESELECTION | VIDEO_PRESTATION
        @NotBlank String nomFichier,
        @Positive long tailleOctets,
        @NotBlank String format      // JPG | PNG | MP4
) {
}
