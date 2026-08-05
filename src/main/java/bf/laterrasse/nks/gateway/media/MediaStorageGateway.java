package bf.laterrasse.nks.gateway.media;

/**
 * Abstraction du stockage média (ADR-04 : Cloudinary retenu pour le MVP, AWS S3 envisagé
 * en V2). Le backend ne fait jamais transiter les fichiers binaires — il génère uniquement
 * les paramètres d'upload signés que le frontend utilise pour uploader directement vers
 * le CDN (§10.4, §12.3).
 */
public interface MediaStorageGateway {

    /**
     * @param resourceType "image" ou "video"
     * @param publicIdHint  identifiant lisible souhaité (ex: candidature-K01-photo)
     */
    PresignedUpload generateUploadParams(String resourceType, String publicIdHint);
}
