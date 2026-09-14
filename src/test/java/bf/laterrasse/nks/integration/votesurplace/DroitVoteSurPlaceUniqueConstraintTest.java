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
import bf.laterrasse.nks.domain.enums.Enums.TypeDroitVote;
import bf.laterrasse.nks.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves invariant #1 from the anti-fraud spec at the real-database level (not just an
 * application-level check): the partial unique index on droits_vote_sur_place.ticket_id
 * WHERE type_droit = 'BASE' (V20__votes_bonus_consommation.sql, replacing the former global
 * UNIQUE constraint from V13) makes it physically impossible to persist two BASE
 * DroitVoteSurPlace rows for the same ticket, even bypassing the service layer entirely —
 * while still allowing multiple BONUS droits for that same ticket (complementary mechanism,
 * cf. VoteSurPlaceService#ajouterConsommationBonus).
 */
class DroitVoteSurPlaceUniqueConstraintTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("A second BASE DroitVoteSurPlace for the same ticket_id violates the partial unique index")
    void deuxiemeDroitBasePourMemeTicket_estRejeteParLIndexUniquePartiel() {
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
                .typeDroit(TypeDroitVote.BASE)
                .build();
        DroitVoteSurPlace saved = droitVoteSurPlaceRepository.save(premier);
        assertThat(saved.getId()).isNotNull();

        DroitVoteSurPlace deuxieme = DroitVoteSurPlace.builder()
                .ticket(qr.getTicket())
                .soiree(soiree)
                .caissier(hotesse)
                .statut(StatutDroitVote.DISPONIBLE)
                .typeDroit(TypeDroitVote.BASE)
                .build();

        assertThatThrownBy(() -> droitVoteSurPlaceRepository.save(deuxieme))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(droitVoteSurPlaceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("A BONUS DroitVoteSurPlace can coexist with the BASE droit of the same ticket")
    void droitBonusPourMemeTicketQueLeBase_estAccepte() {
        Edition edition = creerEdition();
        Phase phase = creerPhase(edition);
        SoireeEvent soiree = creerSoiree(edition, phase, true);
        CategorieTicket categorie = creerCategorieTicket(soiree);
        Reservation reservation = creerReservation(soiree, randomPhone(), 1);
        QRCodeTicket qr = creerBilletComplet(soiree, categorie, reservation, randomPhone());
        Utilisateur hotesse = creerUtilisateurAvecRole(RoleName.HOTESSE);

        DroitVoteSurPlace base = DroitVoteSurPlace.builder()
                .ticket(qr.getTicket())
                .soiree(soiree)
                .caissier(hotesse)
                .statut(StatutDroitVote.DISPONIBLE)
                .typeDroit(TypeDroitVote.BASE)
                .build();
        droitVoteSurPlaceRepository.save(base);

        DroitVoteSurPlace bonus = DroitVoteSurPlace.builder()
                .ticket(qr.getTicket())
                .soiree(soiree)
                .caissier(hotesse)
                .statut(StatutDroitVote.DISPONIBLE)
                .typeDroit(TypeDroitVote.BONUS)
                .build();
        DroitVoteSurPlace savedBonus = droitVoteSurPlaceRepository.save(bonus);

        assertThat(savedBonus.getId()).isNotNull();
        assertThat(droitVoteSurPlaceRepository.count()).isEqualTo(2);

        DroitVoteSurPlace deuxiemeBonus = DroitVoteSurPlace.builder()
                .ticket(qr.getTicket())
                .soiree(soiree)
                .caissier(hotesse)
                .statut(StatutDroitVote.DISPONIBLE)
                .typeDroit(TypeDroitVote.BONUS)
                .build();
        DroitVoteSurPlace savedDeuxiemeBonus = droitVoteSurPlaceRepository.save(deuxiemeBonus);

        assertThat(savedDeuxiemeBonus.getId()).isNotNull();
        assertThat(droitVoteSurPlaceRepository.count()).isEqualTo(3);
    }
}
