package bf.laterrasse.nks.dto.admin;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Une ligne (= un candidat) de la grille de délibération d'une soirée.
 * - notesParJury / totalJuryMoyen / pointsJury : scopés à CETTE soirée (notes_jury.soiree_id).
 * - votesPublicSurPlace / pointsPublicSurPlace : scopés à CETTE soirée également — le total de
 *   référence est celui des candidats qui se sont produits ce soir-là (résolu via leur poule/duo),
 *   PAS cumulé avec d'autres soirées de la même phase.
 * - votesPayants / votesLikes / votesCommentaires / pointsVotesEnLigne : CUMULATIFS sur toute la
 *   phase (le modèle de données ne rattache pas les votes en ligne à une soirée précise, et la
 *   règle métier veut qu'ils s'accumulent sur la durée de la phase) — mêmes totaux que ceux qui
 *   alimentent le classement officiel (WF-07).
 */
public record LigneDeliberationResponse(
        UUID candidatId,
        String codeCandidat,
        String prenom,
        String nom,

        List<NoteParJuryResponse> notesParJury,
        BigDecimal totalJuryMoyen,
        BigDecimal pointsJury,

        long votesPayants,
        long votesLikes,
        long votesCommentaires,
        BigDecimal pointsVotesEnLigne,

        long votesPublicSurPlace,
        BigDecimal pointsPublicSurPlace,

        BigDecimal totalGeneral
) {
}
