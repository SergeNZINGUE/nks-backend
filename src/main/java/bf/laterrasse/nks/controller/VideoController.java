package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Video;
import bf.laterrasse.nks.domain.enums.Enums.StatutVideo;
import bf.laterrasse.nks.dto.video.UploaderVideoRequest;
import bf.laterrasse.nks.dto.video.VideoPublicResponse;
import bf.laterrasse.nks.repository.VideoRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.VideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** §13.4 — US-13/US-14. */
@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final VideoRepository videoRepository;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<Video> uploader(@Valid @RequestBody UploaderVideoRequest request) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.status(201).body(videoService.uploaderPourPhase(utilisateurId, request));
    }

    @GetMapping("/candidat/{candidatId}")
    public ResponseEntity<List<VideoPublicResponse>> videosCandidat(@PathVariable UUID candidatId) {
        List<VideoPublicResponse> videos = videoRepository.findByCandidatId(candidatId).stream()
                .filter(v -> v.getStatut() == StatutVideo.DISPONIBLE)
                .map(VideoPublicResponse::from)
                .toList();
        return ResponseEntity.ok(videos);
    }

    @PutMapping("/{id}/masquer")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> masquer(@PathVariable UUID id) {
        videoService.masquer(id);
        return ResponseEntity.noContent().build();
    }
}
