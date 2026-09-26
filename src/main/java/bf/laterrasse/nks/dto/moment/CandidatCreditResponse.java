package bf.laterrasse.nks.dto.moment;

import bf.laterrasse.nks.domain.Candidat;

import java.util.UUID;

public record CandidatCreditResponse(
        UUID id,
        String codeCandidat,
        String prenom,
        String nom
) {
    public static CandidatCreditResponse from(Candidat c) {
        return new CandidatCreditResponse(
                c.getId(),
                c.getCodeCandidat(),
                c.getUtilisateur().getPrenom(),
                c.getUtilisateur().getNom());
    }
}
