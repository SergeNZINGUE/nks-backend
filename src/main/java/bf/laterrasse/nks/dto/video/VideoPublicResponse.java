package bf.laterrasse.nks.dto.video;

import bf.laterrasse.nks.domain.Video;

import java.time.Instant;
import java.util.UUID;

public record VideoPublicResponse(
        UUID id,
        String urlStreaming,
        String urlThumbnail,
        Integer dureeSecondes,
        Long tailleOctets,
        String titreChanson,
        String statut,
        Instant dateUpload
) {
    public static VideoPublicResponse from(Video v) {
        return new VideoPublicResponse(
                v.getId(),
                v.getUrlStreaming(),
                v.getUrlThumbnail(),
                v.getDureeSecondes(),
                v.getTailleOctets(),
                v.getTitreChanson(),
                v.getStatut().name(),
                v.getDateUpload());
    }
}
