package bf.laterrasse.nks.service;

import bf.laterrasse.nks.config.MediaProperties;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.Video;
import bf.laterrasse.nks.domain.enums.Enums.StatutVideo;
import bf.laterrasse.nks.dto.video.UploaderVideoRequest;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.PhaseRepository;
import bf.laterrasse.nks.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** US-13/US-14 — Upload vidéo de prestation par phase (remplace la précédente le cas échéant). */
@Service
@RequiredArgsConstructor
public class VideoService {

    private final CandidatRepository candidatRepository;
    private final PhaseRepository phaseRepository;
    private final VideoRepository videoRepository;
    private final MediaProperties mediaProperties;

    @Transactional
    public Video uploaderPourPhase(UUID utilisateurId, UploaderVideoRequest request) {
        Candidat candidat = candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new AccesRefuseException("Aucun profil candidat pour cet utilisateur"));
        Phase phase = phaseRepository.findById(request.phaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));

        if (request.tailleOctets() > mediaProperties.getMaxVideoSizeBytes()) {
            throw new ValidationMetierException("Vidéo trop lourde (max "
                    + (mediaProperties.getMaxVideoSizeBytes() / 1024 / 1024) + " Mo)");
        }

        Video video = videoRepository.findByCandidatIdAndPhaseId(candidat.getId(), phase.getId())
                .orElse(Video.builder().candidat(candidat).phase(phase).build());

        video.setUrlStockageOriginale(request.urlVideo());
        video.setUrlStreaming(request.urlVideo());
        video.setDureeSecondes(request.dureeSecondes());
        video.setTailleOctets(request.tailleOctets());
        video.setTitreChanson(request.titreChanson());
        video.setStatut(StatutVideo.DISPONIBLE);

        return videoRepository.save(video);
    }

    @Transactional
    public void masquer(UUID videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException("Vidéo introuvable"));
        video.setStatut(StatutVideo.MASQUEE);
        videoRepository.save(video);
    }
}
