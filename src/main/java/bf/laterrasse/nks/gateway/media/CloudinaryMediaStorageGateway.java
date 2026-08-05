package bf.laterrasse.nks.gateway.media;

import bf.laterrasse.nks.config.MediaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Signature d'upload direct Cloudinary (ADR-04). Le frontend poste en multipart vers
 * `uploadUrl` avec les champs retournés + le fichier ; Cloudinary vérifie la signature
 * SHA-1 des paramètres + api_secret. Voir https://cloudinary.com/documentation/upload_images#signed_uploads
 */
@Component
@RequiredArgsConstructor
public class CloudinaryMediaStorageGateway implements MediaStorageGateway {

    private final MediaProperties mediaProperties;

    @Override
    public PresignedUpload generateUploadParams(String resourceType, String publicIdHint) {
        long timestamp = System.currentTimeMillis() / 1000;
        String publicId = sanitize(publicIdHint) + "-" + timestamp;

        // Paramètres inclus dans la signature (ordre alphabétique, hors api_key/file/resource_type)
        Map<String, String> signedParams = new TreeMap<>();
        signedParams.put("public_id", publicId);
        signedParams.put("timestamp", String.valueOf(timestamp));

        String signature = sign(signedParams, mediaProperties.getCloudinary().getApiSecret());

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("api_key", mediaProperties.getCloudinary().getApiKey());
        fields.put("timestamp", String.valueOf(timestamp));
        fields.put("public_id", publicId);
        fields.put("signature", signature);

        String uploadUrl = "https://api.cloudinary.com/v1_1/%s/%s/upload"
                .formatted(mediaProperties.getCloudinary().getCloudName(), resourceType);

        return new PresignedUpload(uploadUrl, fields, mediaProperties.getPresignedUrlExpirationSeconds(), publicId);
    }

    private String sign(Map<String, String> params, String apiSecret) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> sb.append(k).append('=').append(v).append('&'));
        if (!sb.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }
        sb.append(apiSecret);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de signature Cloudinary", e);
        }
    }

    private String sanitize(String input) {
        return input == null ? "media" : input.replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase();
    }
}
