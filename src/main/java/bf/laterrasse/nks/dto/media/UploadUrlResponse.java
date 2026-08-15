package bf.laterrasse.nks.dto.media;

import java.util.Map;

public record UploadUrlResponse(
        String uploadUrl,
        Map<String, String> fields,
        long expiresInSeconds,
        String publicId
) {
}
