package bf.laterrasse.nks.service;

import bf.laterrasse.nks.config.MediaProperties;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Media;
import bf.laterrasse.nks.domain.enums.Enums.StatutMedia;
import bf.laterrasse.nks.dto.media.EnregistrerPhotoRequest;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final CandidatRepository candidatRepository;
    private final MediaRepository mediaRepository;
    private final MediaProperties mediaProperties;

    @Transactional
    public Media enregistrerPhotoProfil(UUID utilisateurId, EnregistrerPhotoRequest request) {
        Candidat candidat = candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new AccesRefuseException("Aucun profil candidat pour cet utilisateur"));

        validerUrlCloudinary(request.url());

        if (request.tailleOctets() > mediaProperties.getMaxPhotoSizeBytes()) {
            throw new ValidationMetierException("Photo trop lourde : maximum "
                    + (mediaProperties.getMaxPhotoSizeBytes() / 1024 / 1024) + " Mo");
        }

        String format = extraireFormat(request.publicId());

        Media media = mediaRepository.findByCandidatIdAndType(candidat.getId(), request.type())
                .orElse(Media.builder().candidat(candidat).type(request.type()).build());

        media.setUrlStockage(request.url());
        media.setNomFichierOriginal(request.publicId());
        media.setTailleOctets(request.tailleOctets());
        media.setFormat(format);
        media.setStatut(StatutMedia.VALIDE);

        return mediaRepository.save(media);
    }

    private void validerUrlCloudinary(String url) {
        String cloudName = mediaProperties.getCloudinary().getCloudName();
        if (url != null && cloudName != null && !cloudName.isBlank() && url.startsWith("https://")) {
            String expectedPrefix = "https://res.cloudinary.com/" + cloudName + "/";
            if (!url.startsWith(expectedPrefix)) {
                throw new ValidationMetierException("URL média invalide : le fichier doit être hébergé sur Cloudinary");
            }
        }
    }

    private String extraireFormat(String publicId) {
        if (publicId == null) return "JPEG";
        int dot = publicId.lastIndexOf('.');
        if (dot >= 0 && dot < publicId.length() - 1) {
            return publicId.substring(dot + 1).toUpperCase();
        }
        return "JPEG";
    }
}
