package bf.laterrasse.nks.integration.votesurplace;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Candidat;
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
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.integration.AbstractIntegrationTest;
import bf.laterrasse.nks.service.VoteSurPlaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for invariants #1 and #2 of the anti-fraud spec: two real threads, two
 * real transactions, hitting a real PostgreSQL instance (Testcontainers) at the same time.
 * These reproduce, as closely as a JVM test can, the two-hotesses-at-the-same-time and
 * two-taps-on-the-vote-link scenarios described in the handoff doc.
 */
class VoteSurPlaceConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private VoteSurPlaceService voteSurPlaceService;

    @Test
    @DisplayName("Two concurrent validerConsommation() calls on the same ticket: exactly one DroitVoteSurPlace is ever persisted")
    void validerConsommationConcurrente_uneSeuleCreationReussit() throws Exception {
        Edition edition = creerEdition();
        Phase phase = creerPhase(edition);
        SoireeEvent soiree = creerSoiree(edition, phase, true);
        CategorieTicket categorie = creerCategorieTicket(soiree);
        Reservation reservation = creerReservation(soiree, randomPhone(), 1);
        QRCodeTicket qr = creerBilletComplet(soiree, categorie, reservation, randomPhone());
        Utilisateur hotesse1 = creerUtilisateurAvecRole(RoleName.HOTESSE);
        Utilisateur hotesse2 = creerUtilisateurAvecRole(RoleName.HOTESSE);

        UUID qrUuid = qr.getCodeUuid();
        UUID soireeId = soiree.getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLine = new CountDownLatch(1);

        Runnable appelHotesse1 = () -> {
            await(startLine);
            voteSurPlaceService.validerConsommation(qrUuid, soireeId, hotesse1, "10.0.0.1", "device-1");
        };
        Runnable appelHotesse2 = () -> {
            await(startLine);
            voteSurPlaceService.validerConsommation(qrUuid, soireeId, hotesse2, "10.0.0.2", "device-2");
        };

        Future<?> f1 = pool.submit(appelHotesse1);
        Future<?> f2 = pool.submit(appelHotesse2);
        startLine.countDown();

        int succes = 0;
        int echecsAttendus = 0;
        for (Future<?> f : List.of(f1, f2)) {
            try {
                f.get(30, TimeUnit.SECONDS);
                succes++;
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                assertThat(cause).isInstanceOfAny(ConflitEtatException.class, DataIntegrityViolationException.class);
                echecsAttendus++;
            }
        }
        pool.shutdown();

        assertThat(succes).isEqualTo(1);
        assertThat(echecsAttendus).isEqualTo(1);
        assertThat(droitVoteSurPlaceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Two concurrent voter() calls on the same DISPONIBLE droit: exactly one Vote is ever persisted")
    void voterConcurrent_unSeulVoteEstCree() throws Exception {
        Edition edition = creerEdition();
        Phase phase = creerPhase(edition);
        SoireeEvent soiree = creerSoiree(edition, phase, true);
        CategorieTicket categorie = creerCategorieTicket(soiree);
        Reservation reservation = creerReservation(soiree, randomPhone(), 1);
        QRCodeTicket qr = creerBilletComplet(soiree, categorie, reservation, randomPhone());
        Utilisateur hotesse = creerUtilisateurAvecRole(RoleName.HOTESSE);

        Candidat candidatA = creerCandidat(edition);
        Candidat candidatB = creerCandidat(edition);
        affecterCandidatALaSoiree(candidatA, phase, soiree);
        affecterCandidatALaSoiree(candidatB, phase, soiree);

        DroitVoteSurPlace droit = DroitVoteSurPlace.builder()
                .ticket(qr.getTicket())
                .soiree(soiree)
                .caissier(hotesse)
                .statut(StatutDroitVote.DISPONIBLE)
                .build();
        droit = droitVoteSurPlaceRepository.save(droit);

        UUID qrUuid = qr.getCodeUuid();
        UUID soireeId = soiree.getId();
        UUID candidatAId = candidatA.getId();
        UUID candidatBId = candidatB.getId();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch startLine = new CountDownLatch(1);

        Runnable votantA = () -> {
            await(startLine);
            voteSurPlaceService.voter(qrUuid, soireeId, candidatAId, null, null, null, null);
        };
        Runnable votantB = () -> {
            await(startLine);
            voteSurPlaceService.voter(qrUuid, soireeId, candidatBId, null, null, null, null);
        };

        Future<?> f1 = pool.submit(votantA);
        Future<?> f2 = pool.submit(votantB);
        startLine.countDown();

        int succes = 0;
        int echecsAttendus = 0;
        for (Future<?> f : List.of(f1, f2)) {
            try {
                f.get(30, TimeUnit.SECONDS);
                succes++;
            } catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(ConflitEtatException.class);
                echecsAttendus++;
            }
        }
        pool.shutdown();

        assertThat(succes).isEqualTo(1);
        assertThat(echecsAttendus).isEqualTo(1);
        assertThat(voteRepository.findByPhaseId(phase.getId())).hasSize(1);

        DroitVoteSurPlace droitFinal = droitVoteSurPlaceRepository
                .findByTicketIdOrderByDateEmissionAsc(qr.getTicket().getId()).get(0);
        assertThat(droitFinal.getStatut()).isEqualTo(StatutDroitVote.UTILISE);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
