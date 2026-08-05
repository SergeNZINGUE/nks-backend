package bf.laterrasse.nks.dto.candidat;

import bf.laterrasse.nks.domain.Candidat;

import java.util.UUID;

/** Profil public d'un candidat (US-12) — n'expose jamais téléphone/e-mail (RM confidentialité). */
public record CandidatPublicResponse(
        UUID id,
        String codeCandidat,
        String prenom,
        String nom,
        String biographie,
        String chansonPreselection,
        String statutProfil
) {
    public static CandidatPublicResponse from(Candidat c) {
        return new CandidatPublicResponse(
                c.getId(), c.getCodeCandidat(),
                c.getUtilisateur().getPrenom(), c.getUtilisateur().getNom(),
                c.getBiographie(), c.getChansonPreselection(), c.getStatutProfil().name());
    }
}
