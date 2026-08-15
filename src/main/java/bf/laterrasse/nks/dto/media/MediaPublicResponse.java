package bf.laterrasse.nks.dto.media;

import bf.laterrasse.nks.domain.Media;

import java.util.UUID;

public record MediaPublicResponse(
        UUID id,
        String type,
        String urlStockage,
        String format,
        Long tailleOctets,
        String statut
) {
    public static MediaPublicResponse from(Media m) {
        return new MediaPublicResponse(
                m.getId(),
                m.getType().name(),
                m.getUrlStockage(),
                m.getFormat(),
                m.getTailleOctets(),
                m.getStatut().name());
    }
}
