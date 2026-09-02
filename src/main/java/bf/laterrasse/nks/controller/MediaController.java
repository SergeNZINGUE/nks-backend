package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.config.MediaProperties;
import bf.laterrasse.nks.domain.enums.Enums.StatutMedia;
import bf.laterrasse.nks.dto.media.EnregistrerPhotoRequest;
import bf.laterrasse.nks.dto.media.MediaPublicResponse;
import bf.laterrasse.nks.dto.media.UploadUrlRequest;
import bf.laterrasse.nks.dto.media.UploadUrlResponse;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.gateway.media.MediaStorageGateway;
import bf.laterrasse.nks.gateway.media.PresignedUpload;
import bf.laterrasse.nks.repository.MediaRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import java.util.Set;

/**
 * §13.4 — Upload direct client → CDN. Public : utilisé pendant le formulaire d'inscription
 * avant même la création du compte candidat (US-01, US-02).
 */
@RestController
@RequiredArgsConstructor
public class MediaController {

    private static final Set<String> FORMATS_PHOTO = Set.of("JPG", "JPEG", "PNG");
    private static final Set<String> FORMATS_VIDEO = Set.of("MP4");

    private final MediaStorageGateway mediaStorageGateway;
    private final MediaProperties mediaProperties;
    private final MediaRepository mediaRepository;
    private final MediaService mediaService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/medias")
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<MediaPublicResponse> enregistrerPhoto(@Valid @RequestBody EnregistrerPhotoRequest request) {
        var utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.status(201)
                .body(MediaPublicResponse.from(mediaService.enregistrerPhotoProfil(utilisateurId, request)));
    }

    @GetMapping("/medias/candidat/{candidatId}")
    public ResponseEntity<List<MediaPublicResponse>> mediasCandidat(@PathVariable UUID candidatId) {
        List<MediaPublicResponse> medias = mediaRepository.findByCandidatId(candidatId).stream()
                .filter(m -> m.getStatut() == StatutMedia.VALIDE)
                .map(MediaPublicResponse::from)
                .toList();
        return ResponseEntity.ok(medias);
    }

    @PostMapping("/medias/url-upload")
    public ResponseEntity<UploadUrlResponse> urlUploadPhoto(@Valid @RequestBody UploadUrlRequest request) {
        String format = request.format().toUpperCase();
        if (!FORMATS_PHOTO.contains(format)) {
            throw new ValidationMetierException("Format photo non autorisé : " + format + " (JPG/PNG attendu)");
        }
        if (request.tailleOctets() > mediaProperties.getMaxPhotoSizeBytes()) {
            throw new ValidationMetierException("Photo trop lourde : maximum "
                    + (mediaProperties.getMaxPhotoSizeBytes() / 1024 / 1024) + " Mo");
        }
        PresignedUpload upload = mediaStorageGateway.generateUploadParams("image", request.type());
        return ResponseEntity.ok(new UploadUrlResponse(upload.uploadUrl(), upload.fields(),
                upload.expiresInSeconds(), upload.publicId()));
    }

    @PostMapping("/videos/url-upload")
    public ResponseEntity<UploadUrlResponse> urlUploadVideo(@Valid @RequestBody UploadUrlRequest request) {
        String format = request.format().toUpperCase();
        if (!FORMATS_VIDEO.contains(format)) {
            throw new ValidationMetierException("Format vidéo non autorisé : " + format + " (MP4 attendu)");
        }
        if (request.tailleOctets() > mediaProperties.getMaxVideoSizeBytes()) {
            throw new ValidationMetierException("Vidéo trop lourde : maximum "
                    + (mediaProperties.getMaxVideoSizeBytes() / 1024 / 1024) + " Mo (décision client)");
        }
        PresignedUpload upload = mediaStorageGateway.generateUploadParams("video", request.type());
        return ResponseEntity.ok(new UploadUrlResponse(upload.uploadUrl(), upload.fields(),
                upload.expiresInSeconds(), upload.publicId()));
    }
}
