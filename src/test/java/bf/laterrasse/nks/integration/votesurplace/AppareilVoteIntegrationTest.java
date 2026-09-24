package bf.laterrasse.nks.integration.votesurplace;

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
import bf.laterrasse.nks.domain.enums.Enums.TypeDroitVote;
import bf.laterrasse.nks.dto.votesurplace.VoterSurPlaceRequest;
import bf.laterrasse.nks.integration.AbstractIntegrationTest;
import bf.laterrasse.nks.repository.AppareilVoteSurPlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Blocage dur par appareil au vote sur place, de bout en bout (HTTP reel, PostgreSQL reel) :
 * un jeton d'appareil (POST /vote-sur-place/appareil) ne peut voter que pour UN billet par
 * soiree, les votes bonus du meme billet restent autorises, le jeton est obligatoire et signe, et
 * la contrainte UNIQUE(soiree_id, appareil_hash) tranche les votes concurrents.
 */
class AppareilVoteIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @LocalServerPort private int port;
    @Autowired private AppareilVoteSurPlaceRepository appareilRepository;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    /** Une soiree avec vote sur place actif, 1 candidat et 2 billets (A, B) ayant chacun un droit BASE disponible. */
    private static final class Salle {
        SoireeEvent soiree;
        Phase phase;
        Candidat candidat;
        QRCodeTicket billetA;
        QRCodeTicket billetB;
        Utilisateur hotesse;
    }

    private Salle creerSalle() {
        Salle s = new Salle();
        Edition edition = creerEdition();
        s.phase = creerPhase(edition);
        s.soiree = creerSoiree(edition, s.phase, true);
        CategorieTicket categorie = creerCategorieTicket(s.soiree);
        s.hotesse = creerUtilisateurAvecRole(RoleName.HOTESSE);
        s.candidat = creerCandidat(edition);
        affecterCandidatALaSoiree(s.candidat, s.phase, s.soiree);
        s.billetA = creerBilletAvecDroit(s, categorie);
        s.billetB = creerBilletAvecDroit(s, categorie);
        return s;
    }

    private QRCodeTicket creerBilletAvecDroit(Salle s, CategorieTicket categorie) {
        Reservation reservation = creerReservation(s.soiree, randomPhone(), 1);
        QRCodeTicket qr = creerBilletComplet(s.soiree, categorie, reservation, randomPhone());
        creerDroit(s, qr, TypeDroitVote.BASE);
        return qr;
    }

    private void creerDroit(Salle s, QRCodeTicket qr, TypeDroitVote type) {
        droitVoteSurPlaceRepository.save(DroitVoteSurPlace.builder()
                .ticket(qr.getTicket()).soiree(s.soiree).caissier(s.hotesse)
                .statut(StatutDroitVote.DISPONIBLE).typeDroit(type).build());
    }

    private String nouveauJeton() {
        ResponseEntity<JsonNode> reponse = restTemplate.postForEntity(url("/vote-sur-place/appareil"), null, JsonNode.class);
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return reponse.getBody().get("appareilToken").asText();
    }

    private ResponseEntity<JsonNode> voter(Salle s, QRCodeTicket billet, String jeton) {
        HttpHeaders headers = new HttpHeaders();
        if (jeton != null) {
            headers.set("X-Appareil-Token", jeton);
        }
        return restTemplate.exchange(
                url("/vote-sur-place/" + s.soiree.getId() + "/" + billet.getCodeUuid() + "/voter"),
                HttpMethod.POST,
                new HttpEntity<>(new VoterSurPlaceRequest(s.candidat.getId(), null, null, null, null), headers),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> consulter(Salle s, QRCodeTicket billet, String jeton) {
        HttpHeaders headers = new HttpHeaders();
        if (jeton != null) {
            headers.set("X-Appareil-Token", jeton);
        }
        return restTemplate.exchange(
                url("/vote-sur-place/" + s.soiree.getId() + "/" + billet.getCodeUuid()),
                HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private List<DroitVoteSurPlace> droits(QRCodeTicket billet) {
        return droitVoteSurPlaceRepository.findByTicketIdOrderByDateEmissionAsc(billet.getTicket().getId());
    }

    private long nbDroitsUtilises(QRCodeTicket billet) {
        return droits(billet).stream().filter(d -> d.getStatut() == StatutDroitVote.UTILISE).count();
    }

    private static String code(ResponseEntity<JsonNode> reponse) {
        return reponse.getBody().get("code").asText();
    }

    // ------------------------------------------------------------------ (e) un appareil = un billet

    @Test
    @DisplayName("(e) T vote billet A OK ; T sur billet B => 409 APPAREIL_DEJA_UTILISE sans droit consomme ; T bonus billet A OK ; autre appareil sur B OK")
    void unAppareilUnBillet() {
        Salle s = creerSalle();
        creerDroit(s, s.billetA, TypeDroitVote.BONUS);
        String jetonT = nouveauJeton();
        String jetonU = nouveauJeton();

        // T vote pour le billet A (droit BASE)
        ResponseEntity<JsonNode> voteA = voter(s, s.billetA, jetonT);
        assertThat(voteA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nbDroitsUtilises(s.billetA)).isEqualTo(1);
        assertThat(voteRepository.findByPhaseId(s.phase.getId())).hasSize(1);

        // Lecture : T est deja lie a un AUTRE billet pour B, mais pas pour A ; sans jeton => false
        assertThat(consulter(s, s.billetB, jetonT).getBody().get("appareilDejaUtilise").asBoolean()).isTrue();
        assertThat(consulter(s, s.billetA, jetonT).getBody().get("appareilDejaUtilise").asBoolean()).isFalse();
        assertThat(consulter(s, s.billetB, jetonU).getBody().get("appareilDejaUtilise").asBoolean()).isFalse();
        assertThat(consulter(s, s.billetB, null).getBody().get("appareilDejaUtilise").asBoolean()).isFalse();

        // T sur B => 409, aucun droit de B consomme, aucun vote supplementaire
        ResponseEntity<JsonNode> voteB = voter(s, s.billetB, jetonT);
        assertThat(voteB.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(code(voteB)).isEqualTo("APPAREIL_DEJA_UTILISE");
        assertThat(nbDroitsUtilises(s.billetB)).isZero();
        assertThat(droits(s.billetB)).extracting(DroitVoteSurPlace::getStatut).containsOnly(StatutDroitVote.DISPONIBLE);
        assertThat(voteRepository.findByPhaseId(s.phase.getId())).hasSize(1);

        // T utilise le vote BONUS du meme billet A : autorise
        ResponseEntity<JsonNode> voteBonusA = voter(s, s.billetA, jetonT);
        assertThat(voteBonusA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nbDroitsUtilises(s.billetA)).isEqualTo(2);
        assertThat(voteRepository.findByPhaseId(s.phase.getId())).hasSize(2);

        // Un AUTRE appareil peut voter pour B
        ResponseEntity<JsonNode> voteBParU = voter(s, s.billetB, jetonU);
        assertThat(voteBParU.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nbDroitsUtilises(s.billetB)).isEqualTo(1);
        assertThat(voteRepository.findByPhaseId(s.phase.getId())).hasSize(3);

        // Une seule ligne d'appareil par (soiree, appareil) : 2 appareils => 2 lignes
        assertThat(appareilRepository.findAll().stream()
                .filter(a -> a.getSoireeId().equals(s.soiree.getId()))).hasSize(2);
    }

    @Test
    @DisplayName("Le meme jeton reste utilisable sur une AUTRE soiree (le blocage est par soiree)")
    void memeJeton_autreSoiree_autorise() {
        Salle s1 = creerSalle();
        Salle s2 = creerSalle();
        String jeton = nouveauJeton();

        assertThat(voter(s1, s1.billetA, jeton).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(voter(s2, s2.billetA, jeton).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(voter(s2, s2.billetB, jeton).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------ (g) jeton absent / falsifie

    @Test
    @DisplayName("(g) Vote sans jeton ou avec jeton falsifie/tronque => 400 APPAREIL_INCONNU, aucun droit consomme, aucun vote")
    void jetonAbsentOuFalsifie_400() {
        Salle s = creerSalle();
        String valide = nouveauJeton();
        String uuid = valide.substring(0, valide.indexOf('.'));
        String signature = valide.substring(valide.indexOf('.') + 1);
        char dernier = signature.charAt(signature.length() - 1);
        String[] refuses = {
                null,
                "",
                "n-importe-quoi",
                uuid,
                uuid + ".",
                UUID.randomUUID() + "." + signature,
                uuid + "." + signature.substring(0, signature.length() - 1) + (dernier == 'A' ? 'B' : 'A'),
                valide.substring(0, valide.length() - 8),
        };

        for (String jeton : refuses) {
            ResponseEntity<JsonNode> reponse = voter(s, s.billetA, jeton);
            assertThat(reponse.getStatusCode()).as("jeton %s", jeton).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(code(reponse)).as("jeton %s", jeton).isEqualTo("APPAREIL_INCONNU");
        }

        assertThat(nbDroitsUtilises(s.billetA)).isZero();
        assertThat(voteRepository.findByPhaseId(s.phase.getId())).isEmpty();
        assertThat(appareilRepository.findAll().stream()
                .filter(a -> a.getSoireeId().equals(s.soiree.getId()))).isEmpty();
        // Le vrai jeton fonctionne toujours (aucun etat corrompu par les refus).
        assertThat(voter(s, s.billetA, valide).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET avec jeton invalide ou absent : jamais d'erreur, appareilDejaUtilise=false, aucune ecriture")
    void consultationAvecJetonInvalide_estSilencieuse() {
        Salle s = creerSalle();

        ResponseEntity<JsonNode> reponse = consulter(s, s.billetA, "jeton.invalide");

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reponse.getBody().get("appareilDejaUtilise").asBoolean()).isFalse();
        assertThat(appareilRepository.findAll().stream()
                .filter(a -> a.getSoireeId().equals(s.soiree.getId()))).isEmpty();
    }

    // ------------------------------------------------------------------ (f) concurrence

    @Test
    @DisplayName("(f) Le MEME appareil vote sur deux billets differents en parallele : un seul succes (UNIQUE(soiree_id, appareil_hash)), l'autre 409 sans droit consomme")
    void memeAppareil_deuxBillets_enParallele_unSeulSucces() throws Exception {
        for (int tour = 0; tour < 5; tour++) {
            Salle s = creerSalle();
            String jeton = nouveauJeton();

            List<ResponseEntity<JsonNode>> reponses = lancerEnParallele(List.of(
                    () -> voter(s, s.billetA, jeton),
                    () -> voter(s, s.billetB, jeton)));

            List<HttpStatus> statuts = reponses.stream().map(r -> HttpStatus.valueOf(r.getStatusCode().value())).toList();
            assertThat(statuts).as("tour %d", tour).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            ResponseEntity<JsonNode> perdante = reponses.stream()
                    .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).findFirst().orElseThrow();
            assertThat(code(perdante)).isEqualTo("APPAREIL_DEJA_UTILISE");

            assertThat(nbDroitsUtilises(s.billetA) + nbDroitsUtilises(s.billetB)).as("tour %d", tour).isEqualTo(1);
            assertThat(voteRepository.findByPhaseId(s.phase.getId())).hasSize(1);
            assertThat(appareilRepository.findAll().stream()
                    .filter(a -> a.getSoireeId().equals(s.soiree.getId()))).hasSize(1);
        }
    }

    private List<ResponseEntity<JsonNode>> lancerEnParallele(List<Callable<ResponseEntity<JsonNode>>> appels) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(appels.size());
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<JsonNode>>> futurs = appels.stream()
                    .map(appel -> pool.submit(() -> {
                        depart.await();
                        return appel.call();
                    }))
                    .toList();
            depart.countDown();
            List<ResponseEntity<JsonNode>> reponses = new java.util.ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> futur : futurs) {
                reponses.add(futur.get(60, TimeUnit.SECONDS));
            }
            return reponses;
        } finally {
            pool.shutdownNow();
        }
    }
}
