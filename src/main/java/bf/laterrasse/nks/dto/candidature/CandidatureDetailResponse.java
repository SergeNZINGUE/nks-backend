package bf.laterrasse.nks.dto.candidature;

import bf.laterrasse.nks.domain.Candidature;

import java.time.Instant;
import java.util.UUID;

public record CandidatureDetailResponse(
        UUID id,
        String codeCandidat,
        String prenom,
        String nom,
        String telephone,
        String email,
        String statut,
        String motivation,
        String captureFbTiktokUrl,
        Instant dateSoumission,
        String motifRejet
) {
    public static CandidatureDetailResponse from(Candidature c) {
        var utilisateur = c.getCandidat().getUtilisateur();
        return new CandidatureDetailResponse(
                c.getId(),
                c.getCandidat().getCodeCandidat(),
                utilisateur.getPrenom(),
                utilisateur.getNom(),
                utilisateur.getTelephone(),
                utilisateur.getEmail(),
                c.getStatut().name(),
                c.getMotivation(),
                c.getCaptureFbTiktokUrl(),
                c.getDateSoumission(),
                c.getMotifRejet()
        );
    }
}
