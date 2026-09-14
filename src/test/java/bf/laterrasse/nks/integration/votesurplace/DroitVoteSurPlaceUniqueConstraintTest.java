package bf.laterrasse.nks.integration.votesurplace;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.DroitVoteSurPlace;
import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.RoleName;
import bf.laterrasse.nks.domain.enums.Enums.StatutDroitVote;
import bf.laterrasse.nks.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves invariant #1 from the anti-fraud spec at the real-database level (not just an
 * application-level check): the UNIQUE constraint on droits_vote_sur_place.ticket_id
 * (V13__caissier_droit_vote_sur_place.sql) makes it physically impossible to persist two
 * DroitVoteSurPlace rows for the same ticket, even bypassing the service layer entirely.
 */
class DroitVoteSurPlaceUniqueConstraintTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("A second DroitVoteSurPlace for the same ticket_id violates the DB UNIQUE constraint")
    void deuxiemeDroitPourMemeTicket_estRejeteParLaContrainteUnique() {
        Edition edition = creerEdition();
        Phase phase = creerPhase(edition);
        SoireeEvent soiree = creerSoiree(edition, phase, true);
        CategorieTicket categorie = creerCategorieTicket(soiree);
        Reservation reservation = creerReservation(soiree, randomPhone(), 1);
        QRCodeTicket qr = creerBilletComplet(soiree, categorie, reservation, randomPhone());
        Utilisateur hotesse = creerUtilisateurAvecRole(RoleName.HOTESSE);

        DroitVoteSurPlace premier = DroitVoteSurPlace.builder()
                .ticket(qr.getTicket())
                .soiree(soiree)
                .caissier(hotesse)
                .statut(StatutDroitVote.DISPONIBLE)
                .build();
        DroitVoteSurPlace saved = droitVoteSurPlaceRepository.save(premier);
        assertThat(saved.getId()).isNotNull();

        DroitVoteSurPlace deuxieme = DroitVoteSurPlace.builder()
                .ticket(qr.getTicket())
                .soiree(soiree)
                .caissier(hotesse)
                .statut(StatutDroitVote.DISPONIBLE)
                .build();

        assertThatThrownBy(() -> droitVoteSurPlaceRepository.save(deuxieme))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(droitVoteSurPlaceRepository.count()).isEqualTo(1);
    }
}
