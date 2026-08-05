package bf.laterrasse.nks.dto.media;

import java.util.Map;

public record UploadUrlResponse(
        String uploadUrl,
        Map<String, String> champs,
        long expireDansSecondes,
        String publicId
) {
}
