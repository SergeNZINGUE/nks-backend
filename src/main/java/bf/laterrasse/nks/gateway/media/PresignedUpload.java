package bf.laterrasse.nks.gateway.media;

import java.util.Map;

/**
 * Paramètres à transmettre au frontend pour un upload direct client → CDN (§10.4) :
 * le backend ne relaie jamais le binaire, il ne fait que signer la requête d'upload.
 */
public record PresignedUpload(
        String uploadUrl,
        Map<String, String> fields,
        long expiresInSeconds,
        String publicId
) {
}
