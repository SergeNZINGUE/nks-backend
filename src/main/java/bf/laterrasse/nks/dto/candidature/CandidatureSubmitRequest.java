package bf.laterrasse.nks.dto.candidature;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * §13.3 POST /candidatures. Les URLs de médias sont déjà connues à ce stade : le frontend
 * a uploadé directement vers le CDN via les paramètres signés retournés par
 * POST /medias/url-upload et POST /videos/url-upload (§11.4, §10.4).
 */
public record CandidatureSubmitRequest(
        @NotBlank String prenom,
        @NotBlank String nom,
        @NotNull LocalDate dateNaissance,
        @NotBlank String telephone,
        @NotBlank @Email String email,
        @NotBlank String chansonPreselection,
        @Size(max = 1500, message = "La motivation est limitée à ~200 mots") String motivation,

        @NotBlank String urlPhoto,
        @NotBlank String formatPhoto,
        @Positive long taillePhotoOctets,

        @NotBlank String urlVideo,
        @Positive int dureeVideoSecondes,
        @Positive long tailleVideoOctets,

        @NotBlank String urlCaptureSocial,

        @NotNull UUID editionId
) {
}
