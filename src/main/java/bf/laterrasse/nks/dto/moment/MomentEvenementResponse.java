package bf.laterrasse.nks.dto.moment;

import bf.laterrasse.nks.domain.MomentEvenement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Réponse unique pour les 3 contextes (grille publique, "mes envois" candidat, file de
 * modération admin) — le frontend résout l'affichage du crédit : candidatsTagues non
 * vide → tous les noms ; sinon candidatUploadeur* → ce nom ; sinon "Équipe NKS".
 */
public record MomentEvenementResponse(
        UUID id,
        String type,
        String urlStockage,
        String legende,
        boolean enVedette,
        String statut,
        String motifRejet,
        Instant dateUpload,
        UUID soireeId,
        String soireeNom,
        UUID candidatUploadeurId,
        String candidatUploadeurCode,
        List<CandidatCreditResponse> candidatsTagues
) {
    public static MomentEvenementResponse from(MomentEvenement m) {
        return new MomentEvenementResponse(
                m.getId(),
                m.getType().name(),
                m.getUrlStockage(),
                m.getLegende(),
                m.isEnVedette(),
                m.getStatut().name(),
                m.getMotifRejet(),
                m.getDateUpload(),
                m.getSoiree() != null ? m.getSoiree().getId() : null,
                m.getSoiree() != null ? m.getSoiree().getNom() : null,
                m.getCandidatUploadeur() != null ? m.getCandidatUploadeur().getId() : null,
                m.getCandidatUploadeur() != null ? m.getCandidatUploadeur().getCodeCandidat() : null,
                m.getCandidatsTagues().stream().map(CandidatCreditResponse::from).toList());
    }
}
