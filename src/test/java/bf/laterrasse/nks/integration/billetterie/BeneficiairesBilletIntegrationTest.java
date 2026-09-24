package bf.laterrasse.nks.integration.billetterie;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.ReservationBeneficiaire;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.enums.Enums.NomCategorieTicket;
import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import bf.laterrasse.nks.dto.billetterie.BeneficiaireBilletRequest;
import bf.laterrasse.nks.dto.billetterie.ReservationRequest;
import bf.laterrasse.nks.event.PaiementConfirmeEvent;
import bf.laterrasse.nks.event.PaiementEchoueEvent;
import bf.laterrasse.nks.integration.AbstractIntegrationTest;
import bf.laterrasse.nks.job.ReservationExpiryJob;
import bf.laterrasse.nks.repository.ReservationBeneficiaireRepository;
import bf.laterrasse.nks.service.BilletterieService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Un numero de telephone par billet" de bout en bout (HTTP reel + PostgreSQL reel via
 * Testcontainers, index unique partiel dédié exerce pour de vrai) : reservation avec un numero par
 * place, unicite par soiree (billets legacy et pre-reservations PENDING comprises), liberation des
 * numeros (expiration, echec de paiement, annulation, cloture de soiree), concurrence, et masquage
 * du numero dans la reponse "Mes tickets".
 */
class BeneficiairesBilletIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @LocalServerPort private int port;
    @Autowired private ReservationBeneficiaireRepository beneficiaireRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private BilletterieService billetterieService;
    @Autowired private ReservationExpiryJob reservationExpiryJob;
    @Autowired private bf.laterrasse.nks.repository.AppareilVoteSurPlaceRepository appareilRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    // ------------------------------------------------------------------ fixtures / helpers

    private SoireeEvent creerSoireeFuture() {
        Edition edition = creerEdition();
        Phase phase = creerPhase(edition);
        SoireeEvent soiree = creerSoiree(edition, phase, false);
        soiree.setDateHeure(Instant.now().plus(5, ChronoUnit.DAYS));
        return soireeEventRepository.save(soiree);
    }

    private CategorieTicket creerCategorie(SoireeEvent soiree, NomCategorieTicket nom) {
        return categorieTicketRepository.save(CategorieTicket.builder()
                .soiree(soiree).nom(nom).prix(BigDecimal.valueOf(1000)).nbPlacesDisponibles(100).build());
    }

    private int placesReservees(CategorieTicket categorie) {
        return categorieTicketRepository.findById(categorie.getId()).orElseThrow().getNbPlacesReservees();
    }

    private static BeneficiaireBilletRequest benef(String telephone, String nom) {
        return new BeneficiaireBilletRequest(telephone, nom);
    }

    private ResponseEntity<JsonNode> initier(SoireeEvent soiree, CategorieTicket categorie, String telephonePayeur,
                                             List<BeneficiaireBilletRequest> beneficiaires) {
        ReservationRequest body = new ReservationRequest(soiree.getId(), categorie.getId(), beneficiaires.size(),
                "Payeur Test", telephonePayeur, null, beneficiaires);
        return restTemplate.postForEntity(url("/reservations/initier"), body, JsonNode.class);
    }

    private ResponseEntity<JsonNode> initierUnNumero(SoireeEvent soiree, CategorieTicket categorie, String telephone) {
        return initier(soiree, categorie, randomPhone(), List.of(benef(telephone, "Invite")));
    }

    private static UUID reservationId(ResponseEntity<JsonNode> reponse) {
        return UUID.fromString(reponse.getBody().get("reservationId").asText());
    }

    private void confirmerPaiement(ResponseEntity<JsonNode> reponse) {
        UUID paiementId = UUID.fromString(reponse.getBody().get("paiementId").asText());
        eventPublisher.publishEvent(new PaiementConfirmeEvent(paiementId, TypePaiement.BILLET, null,
                BigDecimal.valueOf(1000), "ref-" + paiementId, "+22670000000"));
    }

    private boolean numeroActif(SoireeEvent soiree, String telephone) {
        return !beneficiaireRepository.findTelephonesActifs(soiree.getId(), List.of(telephone)).isEmpty();
    }

    // ------------------------------------------------------------------ (a) nominal

    @Test
    @DisplayName("(a) 2 beneficiaires distincts : reservation OK, puis apres confirmation 2 billets portant chacun le bon telephone/nom")
    void deuxBeneficiaires_reservationPuisDeuxBilletsAvecLeBonTelephone() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t1 = randomPhone();
        String t2 = randomPhone();

        ResponseEntity<JsonNode> reponse = initier(soiree, categorie, randomPhone(),
                List.of(benef(t1, "Alice"), benef(t2, "Bob")));

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reponse.getBody().get("statut").asText()).isEqualTo("PENDING");
        assertThat(placesReservees(categorie)).isEqualTo(2);
        List<ReservationBeneficiaire> beneficiaires =
                beneficiaireRepository.findByReservationIdOrderByPositionAsc(reservationId(reponse));
        assertThat(beneficiaires).extracting(ReservationBeneficiaire::getTelephone).containsExactly(t1, t2);
        assertThat(beneficiaires).allMatch(ReservationBeneficiaire::isActif);
        // Aucun billet avant la confirmation du paiement.
        assertThat(ticketRepository.findByReservationId(reservationId(reponse))).isEmpty();

        confirmerPaiement(reponse);

        List<Ticket> tickets = ticketRepository.findByReservationId(reservationId(reponse));
        assertThat(tickets).hasSize(2);
        assertThat(tickets).extracting(Ticket::getStatut).containsOnly(StatutTicket.EMIS);
        assertThat(tickets).extracting(Ticket::getNomSpectateur, Ticket::getTelephoneSpectateur)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Alice", t1),
                        org.assertj.core.groups.Tuple.tuple("Bob", t2));
        assertThat(reservationRepository.findById(reservationId(reponse)).orElseThrow().getStatut())
                .isEqualTo(StatutReservation.CONFIRMEE);
    }

    @Test
    @DisplayName("Taille de beneficiaires != nbPlaces et doublon interne => 400, aucune place consommee")
    void validationHttp_tailleEtDoublon() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t = randomPhone();

        ReservationRequest tropPeu = new ReservationRequest(soiree.getId(), categorie.getId(), 2, "Payeur",
                randomPhone(), null, List.of(benef(t, null)));
        ResponseEntity<JsonNode> r1 = restTemplate.postForEntity(url("/reservations/initier"), tropPeu, JsonNode.class);
        ResponseEntity<JsonNode> r2 = initier(soiree, categorie, randomPhone(),
                List.of(benef(t, null), benef("0022670" + t.substring(6), null)));

        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(placesReservees(categorie)).isZero();
        assertThat(numeroActif(soiree, t)).isFalse();
    }

    // ------------------------------------------------------------------ (b) numero deja pris

    @Test
    @DisplayName("(b) Numero deja pris par une pre-reservation PENDING => 409, nb_places_reservees inchange, aucun beneficiaire ecrit")
    void numeroDejaPris_409_sansFuiteDePlaces() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String pris = randomPhone();
        String libre = randomPhone();
        assertThat(initierUnNumero(soiree, categorie, pris).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(placesReservees(categorie)).isEqualTo(1);

        // Un des deux numeros est deja pris : toute la reservation est refusee (pas de reservation partielle).
        ResponseEntity<JsonNode> refuse = initier(soiree, categorie, randomPhone(),
                List.of(benef(libre, "Libre"), benef(pris, "Pris")));

        assertThat(refuse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(refuse.getBody().get("message").asText()).doesNotContain(pris);
        assertThat(placesReservees(categorie)).isEqualTo(1);
        assertThat(numeroActif(soiree, libre)).isFalse();
        // Le numero libre reste utilisable seul.
        assertThat(initierUnNumero(soiree, categorie, libre).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(placesReservees(categorie)).isEqualTo(2);
    }

    @Test
    @DisplayName("(b) Numero porte par un billet legacy EMIS/UTILISE => 409 ; ANNULE ou EXPIRE => reutilisable")
    void numeroSurBilletLegacy_bloque_sauAnnuleOuExpire() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        Reservation legacy = creerReservation(soiree, randomPhone(), 4);
        String emis = randomPhone();
        String utilise = randomPhone();
        String annule = randomPhone();
        String expire = randomPhone();
        creerBilletComplet(soiree, categorie, legacy, emis);
        creerBilletComplet(soiree, categorie, legacy, utilise).getTicket();
        Ticket tUtilise = ticketRepository.findByReservationId(legacy.getId()).stream()
                .filter(t -> utilise.equals(t.getTelephoneSpectateur())).findFirst().orElseThrow();
        tUtilise.setStatut(StatutTicket.UTILISE);
        ticketRepository.save(tUtilise);
        Ticket tAnnule = creerBilletComplet(soiree, categorie, legacy, annule).getTicket();
        tAnnule.setStatut(StatutTicket.ANNULE);
        ticketRepository.save(tAnnule);
        Ticket tExpire = creerBilletComplet(soiree, categorie, legacy, expire).getTicket();
        tExpire.setStatut(StatutTicket.EXPIRE);
        ticketRepository.save(tExpire);

        assertThat(initierUnNumero(soiree, categorie, emis).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(initierUnNumero(soiree, categorie, utilise).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(placesReservees(categorie)).isZero();

        assertThat(initierUnNumero(soiree, categorie, annule).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initierUnNumero(soiree, categorie, expire).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(placesReservees(categorie)).isEqualTo(2);
    }

    @Test
    @DisplayName("Un numero est libre pour une AUTRE soiree : l'unicite est par soiree")
    void numeroPris_reutilisableSurUneAutreSoiree() {
        SoireeEvent soiree1 = creerSoireeFuture();
        SoireeEvent soiree2 = creerSoireeFuture();
        CategorieTicket cat1 = creerCategorie(soiree1, NomCategorieTicket.STANDARD);
        CategorieTicket cat2 = creerCategorie(soiree2, NomCategorieTicket.STANDARD);
        String t = randomPhone();

        assertThat(initierUnNumero(soiree1, cat1, t).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initierUnNumero(soiree2, cat2, t).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ------------------------------------------------------------------ (c) liberation

    @Test
    @DisplayName("(c) Expiration de la pre-reservation : numero libere (actif=false) puis reutilisable")
    void expirationPreReservation_liberreLeNumero() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t = randomPhone();
        ResponseEntity<JsonNode> premiere = initierUnNumero(soiree, categorie, t);
        assertThat(initierUnNumero(soiree, categorie, t).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Reservation reservation = reservationRepository.findById(reservationId(premiere)).orElseThrow();
        reservation.setDateExpiration(Instant.now().minus(1, ChronoUnit.MINUTES));
        reservationRepository.save(reservation);
        reservationExpiryJob.expirerPreReservations();

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getStatut())
                .isEqualTo(StatutReservation.EXPIREE);
        assertThat(beneficiaireRepository.findByReservationIdOrderByPositionAsc(reservation.getId()))
                .noneMatch(ReservationBeneficiaire::isActif);
        assertThat(placesReservees(categorie)).isZero();
        assertThat(initierUnNumero(soiree, categorie, t).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(placesReservees(categorie)).isEqualTo(1);
    }

    @Test
    @DisplayName("(c) Paiement echoue : numero libere puis reutilisable")
    void paiementEchoue_liberreLeNumero() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t = randomPhone();
        ResponseEntity<JsonNode> premiere = initierUnNumero(soiree, categorie, t);
        UUID paiementId = UUID.fromString(premiere.getBody().get("paiementId").asText());

        eventPublisher.publishEvent(new PaiementEchoueEvent(paiementId, TypePaiement.BILLET));

        assertThat(numeroActif(soiree, t)).isFalse();
        assertThat(placesReservees(categorie)).isZero();
        assertThat(initierUnNumero(soiree, categorie, t).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("(c) Annulation d'une reservation confirmee : numero libere, billet ANNULE, numero reutilisable")
    void annulationReservation_liberreLeNumero() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t = randomPhone();
        String telephonePayeur = randomPhone();
        ResponseEntity<JsonNode> reponse = initier(soiree, categorie, telephonePayeur, List.of(benef(t, "Invite")));
        confirmerPaiement(reponse);
        assertThat(initierUnNumero(soiree, categorie, t).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        billetterieService.annulerReservation(reservationId(reponse), telephonePayeur);

        assertThat(ticketRepository.findByReservationId(reservationId(reponse)))
                .extracting(Ticket::getStatut).containsOnly(StatutTicket.ANNULE);
        assertThat(numeroActif(soiree, t)).isFalse();
        assertThat(initierUnNumero(soiree, categorie, t).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("(c) Cloture de soiree : les billets EMIS expirent, leurs numeros sont liberes ; un billet UTILISE garde le sien")
    void clotureSoiree_liberreLesNumerosDesBilletsExpires() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String tEmis = randomPhone();
        String tUtilise = randomPhone();
        ResponseEntity<JsonNode> reponse = initier(soiree, categorie, randomPhone(),
                List.of(benef(tEmis, "Emis"), benef(tUtilise, "Utilise")));
        confirmerPaiement(reponse);
        Ticket billetUtilise = ticketRepository.findByReservationId(reservationId(reponse)).stream()
                .filter(t -> tUtilise.equals(t.getTelephoneSpectateur())).findFirst().orElseThrow();
        billetUtilise.setStatut(StatutTicket.UTILISE);
        ticketRepository.save(billetUtilise);

        billetterieService.expirerTicketsSoiree(soiree.getId());

        assertThat(ticketRepository.findByReservationId(reservationId(reponse)))
                .extracting(Ticket::getTelephoneSpectateur, Ticket::getStatut)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(tEmis, StatutTicket.EXPIRE),
                        org.assertj.core.groups.Tuple.tuple(tUtilise, StatutTicket.UTILISE));
        assertThat(numeroActif(soiree, tEmis)).isFalse();
        assertThat(numeroActif(soiree, tUtilise)).isTrue();
        assertThat(initierUnNumero(soiree, categorie, tEmis).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initierUnNumero(soiree, categorie, tUtilise).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------ audit : paiement tardif, annulation, appareils, plafond

    private void expirerLaPreReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setDateExpiration(Instant.now().minus(1, ChronoUnit.MINUTES));
        reservationRepository.save(reservation);
        reservationExpiryJob.expirerPreReservations();
        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatut())
                .isEqualTo(StatutReservation.EXPIREE);
    }

    @Test
    @DisplayName("(1) Paiement confirme APRES expiration, numero toujours libre : places et numero reactives, billet emis")
    void paiementTardif_numeroLibre_emetLesBillets() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t = randomPhone();
        ResponseEntity<JsonNode> reponse = initier(soiree, categorie, randomPhone(), List.of(benef(t, "Invite")));
        UUID reservationId = reservationId(reponse);
        expirerLaPreReservation(reservationId);
        assertThat(numeroActif(soiree, t)).isFalse();
        assertThat(placesReservees(categorie)).isZero();

        confirmerPaiement(reponse);

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatut())
                .isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(ticketRepository.findByReservationId(reservationId))
                .extracting(Ticket::getTelephoneSpectateur).containsExactly(t);
        assertThat(numeroActif(soiree, t)).isTrue();
        assertThat(placesReservees(categorie)).isEqualTo(1);
        assertThat(initierUnNumero(soiree, categorie, t).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("(1) Paiement confirme APRES expiration, numero repris entre-temps : aucun billet, reservation reste EXPIREE, aucune exception, l'autre reservation intacte")
    void paiementTardif_numeroRepris_neEmetPas() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t = randomPhone();
        ResponseEntity<JsonNode> reponseA = initier(soiree, categorie, randomPhone(), List.of(benef(t, "A")));
        UUID reservationA = reservationId(reponseA);
        expirerLaPreReservation(reservationA);
        ResponseEntity<JsonNode> reponseB = initier(soiree, categorie, randomPhone(), List.of(benef(t, "B")));
        assertThat(reponseB.getStatusCode()).isEqualTo(HttpStatus.OK);

        org.assertj.core.api.Assertions.assertThatCode(() -> confirmerPaiement(reponseA)).doesNotThrowAnyException();

        assertThat(reservationRepository.findById(reservationA).orElseThrow().getStatut())
                .isEqualTo(StatutReservation.EXPIREE);
        assertThat(ticketRepository.findByReservationId(reservationA)).isEmpty();
        assertThat(beneficiaireRepository.findByReservationIdOrderByPositionAsc(reservationA))
                .noneMatch(ReservationBeneficiaire::isActif);
        // La reservation B (qui a legitimement le numero) n'est pas touchee ; le compteur ne compte que B.
        assertThat(beneficiaireRepository.findByReservationIdOrderByPositionAsc(reservationId(reponseB)))
                .allMatch(ReservationBeneficiaire::isActif);
        assertThat(placesReservees(categorie)).isEqualTo(1);
    }

    @Test
    @DisplayName("(5) Annulation refusee si un billet est UTILISE : reservation, billets et numeros inchanges")
    void annulation_refuseeSiBilletUtilise() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String telephonePayeur = randomPhone();
        String t1 = randomPhone();
        String t2 = randomPhone();
        ResponseEntity<JsonNode> reponse = initier(soiree, categorie, telephonePayeur,
                List.of(benef(t1, "Un"), benef(t2, "Deux")));
        confirmerPaiement(reponse);
        UUID reservationId = reservationId(reponse);
        Ticket billet = ticketRepository.findByReservationId(reservationId).stream()
                .filter(t -> t1.equals(t.getTelephoneSpectateur())).findFirst().orElseThrow();
        billet.setStatut(StatutTicket.UTILISE);
        ticketRepository.save(billet);

        assertThatThrownBy(() -> billetterieService.annulerReservation(reservationId, telephonePayeur))
                .isInstanceOf(bf.laterrasse.nks.exception.ConflitEtatException.class);

        assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatut())
                .isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(ticketRepository.findByReservationId(reservationId))
                .extracting(Ticket::getStatut).containsExactlyInAnyOrder(StatutTicket.UTILISE, StatutTicket.EMIS);
        assertThat(numeroActif(soiree, t1)).isTrue();
        assertThat(numeroActif(soiree, t2)).isTrue();
        assertThat(placesReservees(categorie)).isEqualTo(2);
    }

    private void lierAppareil(SoireeEvent soiree, Ticket ticket) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s ->
                appareilRepository.insererSiAbsent(UUID.randomUUID(), soiree.getId(), "hash-" + ticket.getId(),
                        ticket.getId(), "10.0.0.1", "agent", null));
    }

    private boolean appareilLie(SoireeEvent soiree, Ticket ticket) {
        return appareilRepository.findTicketIdBySoireeAndHash(soiree.getId(), "hash-" + ticket.getId()).isPresent();
    }

    @Test
    @DisplayName("(9) Annulation d'une reservation : les appareils de vote de ses billets sont supprimes")
    void annulation_supprimeLesAppareils() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String telephonePayeur = randomPhone();
        ResponseEntity<JsonNode> reponse = initier(soiree, categorie, telephonePayeur, List.of(benef(randomPhone(), "Un")));
        confirmerPaiement(reponse);
        Ticket billet = ticketRepository.findByReservationId(reservationId(reponse)).get(0);
        lierAppareil(soiree, billet);
        assertThat(appareilLie(soiree, billet)).isTrue();

        billetterieService.annulerReservation(reservationId(reponse), telephonePayeur);

        assertThat(appareilLie(soiree, billet)).isFalse();
    }

    @Test
    @DisplayName("(9) Cloture de soiree : appareil supprime pour le billet EXPIRE, conserve pour le billet UTILISE")
    void cloture_supprimeLesAppareils_saufBilletUtilise() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String tEmis = randomPhone();
        String tUtilise = randomPhone();
        ResponseEntity<JsonNode> reponse = initier(soiree, categorie, randomPhone(),
                List.of(benef(tEmis, "Emis"), benef(tUtilise, "Utilise")));
        confirmerPaiement(reponse);
        List<Ticket> billets = ticketRepository.findByReservationId(reservationId(reponse));
        Ticket emis = billets.stream().filter(t -> tEmis.equals(t.getTelephoneSpectateur())).findFirst().orElseThrow();
        Ticket utilise = billets.stream().filter(t -> tUtilise.equals(t.getTelephoneSpectateur())).findFirst().orElseThrow();
        utilise.setStatut(StatutTicket.UTILISE);
        ticketRepository.save(utilise);
        lierAppareil(soiree, emis);
        lierAppareil(soiree, utilise);

        billetterieService.expirerTicketsSoiree(soiree.getId());

        assertThat(appareilLie(soiree, emis)).isFalse();
        assertThat(appareilLie(soiree, utilise)).isTrue();
    }

    @Test
    @DisplayName("(2) Plafond serveur : 3e pre-reservation PENDING du meme payeur pour la soiree => 409 ; nbPlaces=11 => 400")
    void plafonds_preReservationsEtNbPlaces() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String payeur = randomPhone();
        assertThat(initier(soiree, categorie, payeur, List.of(benef(randomPhone(), "1"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(initier(soiree, categorie, payeur, List.of(benef(randomPhone(), "2"))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> troisieme = initier(soiree, categorie, payeur, List.of(benef(randomPhone(), "3")));
        assertThat(troisieme.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(troisieme.getBody().get("message").asText()).contains("réservations en attente");
        assertThat(placesReservees(categorie)).isEqualTo(2);

        List<BeneficiaireBilletRequest> onze = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            onze.add(benef(randomPhone(), "n" + i));
        }
        assertThat(initier(soiree, categorie, randomPhone(), onze).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(placesReservees(categorie)).isEqualTo(2);
    }

    @Test
    @DisplayName("(3) Numero deja pris => 409 avec message generique (aucun numero, meme masque)")
    void messageUnicite_generiqueEnHttp() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t = randomPhone();
        assertThat(initierUnNumero(soiree, categorie, t).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> refus = initierUnNumero(soiree, categorie, t);

        assertThat(refus.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String message = refus.getBody().get("message").asText();
        assertThat(message).contains("L'un des numéros saisis est déjà utilisé pour un billet de cette soirée")
                .doesNotContain("•").doesNotContain(t.substring(t.length() - 4));
    }

    // ------------------------------------------------------------------ index dédié (garde-fou base)

    @Test
    @DisplayName("Index unique partiel : 2 beneficiaires ACTIFS du meme numero et de la meme soiree sont refuses par la base ; inactif ou autre soiree OK")
    void indexUniquePartiel_refuseLeDoublonActif() {
        SoireeEvent soiree = creerSoireeFuture();
        SoireeEvent autreSoiree = creerSoireeFuture();
        Reservation r1 = creerReservation(soiree, randomPhone(), 1);
        Reservation r2 = creerReservation(soiree, randomPhone(), 1);
        Reservation r3 = creerReservation(autreSoiree, randomPhone(), 1);
        String t = randomPhone();

        ReservationBeneficiaire premier = beneficiaireRepository.saveAndFlush(beneficiaire(r1, soiree, t, true));

        assertThatThrownBy(() -> beneficiaireRepository.saveAndFlush(beneficiaire(r2, soiree, t, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Autre soiree : autorise.
        beneficiaireRepository.saveAndFlush(beneficiaire(r3, autreSoiree, t, true));
        // Une fois le premier libere, le numero est reutilisable dans la meme soiree.
        // Les requetes @Modifying exigent une transaction (fournie par les services en production).
        Integer desactives = new TransactionTemplate(transactionManager)
                .execute(status -> beneficiaireRepository.desactiverParReservation(r1.getId()));
        assertThat(desactives).isEqualTo(1);
        beneficiaireRepository.saveAndFlush(beneficiaire(r2, soiree, t, true));
        assertThat(beneficiaireRepository.findById(premier.getId()).orElseThrow().isActif()).isFalse();
    }

    private static ReservationBeneficiaire beneficiaire(Reservation reservation, SoireeEvent soiree, String tel, boolean actif) {
        return ReservationBeneficiaire.builder().reservationId(reservation.getId()).soireeId(soiree.getId())
                .position((short) 0).nom("X").telephone(tel).actif(actif).build();
    }

    // ------------------------------------------------------------------ (d) concurrence

    private List<ResponseEntity<JsonNode>> lancerEnParallele(List<Callable<ResponseEntity<JsonNode>>> appels) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(appels.size());
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<JsonNode>>> futurs = new ArrayList<>();
            for (Callable<ResponseEntity<JsonNode>> appel : appels) {
                futurs.add(pool.submit(() -> {
                    depart.await();
                    return appel.call();
                }));
            }
            depart.countDown();
            List<ResponseEntity<JsonNode>> reponses = new ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> futur : futurs) {
                reponses.add(futur.get(60, TimeUnit.SECONDS));
            }
            return reponses;
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertUnSeulSucces(List<ResponseEntity<JsonNode>> reponses) {
        long succes = reponses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long conflits = reponses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
        assertThat(succes).as("statuts : %s", reponses.stream().map(ResponseEntity::getStatusCode).toList()).isEqualTo(1);
        assertThat(conflits).isEqualTo(reponses.size() - 1L);
    }

    @Test
    @DisplayName("(d) Deux reservations simultanees, MEME numero, MEME categorie : une seule reussit, aucune fuite de places")
    void concurrence_memeNumero_memeCategorie() throws Exception {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        for (int tour = 0; tour < 3; tour++) {
            String t = randomPhone();
            int avant = placesReservees(categorie);

            List<ResponseEntity<JsonNode>> reponses = lancerEnParallele(List.of(
                    () -> initierUnNumero(soiree, categorie, t),
                    () -> initierUnNumero(soiree, categorie, t)));

            assertUnSeulSucces(reponses);
            assertThat(placesReservees(categorie)).as("tour %d", tour).isEqualTo(avant + 1);
            assertThat(beneficiaireRepository.findTelephonesActifs(soiree.getId(), List.of(t))).containsExactly(t);
        }
    }

    @Test
    @DisplayName("(d) Deux reservations simultanees, MEME numero, categories DIFFERENTES (pas de verrou commun => c'est l'index dédié qui tranche) : une seule reussit, aucune fuite de places")
    void concurrence_memeNumero_categoriesDifferentes_indexDedie() throws Exception {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket standard = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        CategorieTicket vip = creerCategorie(soiree, NomCategorieTicket.VIP);
        for (int tour = 0; tour < 5; tour++) {
            String t = randomPhone();
            int avantStandard = placesReservees(standard);
            int avantVip = placesReservees(vip);

            List<ResponseEntity<JsonNode>> reponses = lancerEnParallele(List.of(
                    () -> initierUnNumero(soiree, standard, t),
                    () -> initierUnNumero(soiree, vip, t)));

            assertUnSeulSucces(reponses);
            // Somme des places reservees : +1 exactement (la perdante a tout annule, places comprises).
            assertThat(placesReservees(standard) + placesReservees(vip))
                    .as("tour %d", tour).isEqualTo(avantStandard + avantVip + 1);
            assertThat(beneficiaireRepository.findTelephonesActifs(soiree.getId(), List.of(t))).containsExactly(t);
        }
    }

    // ------------------------------------------------------------------ categorie stockee sur la reservation
    // (correctif bug "1ere categorie de la soiree devinee" : une soiree a 2 categories actives, on ne doit
    // jamais toucher aux places de l'AUTRE categorie que celle reellement reservee.)

    @Test
    @DisplayName("(categorie stockee) Expiration d'une pre-reservation sur la categorie VIP : SEULE VIP perd ses places, STANDARD inchangee")
    void expiration_neTouchePasLAutreCategorie() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket standard = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        CategorieTicket vip = creerCategorie(soiree, NomCategorieTicket.VIP);
        // Une reservation confirmee sur STANDARD sert de temoin : ses places ne doivent jamais bouger.
        ResponseEntity<JsonNode> temoin = initierUnNumero(soiree, standard, randomPhone());
        confirmerPaiement(temoin);
        assertThat(placesReservees(standard)).isEqualTo(1);

        ResponseEntity<JsonNode> reponseVip = initierUnNumero(soiree, vip, randomPhone());
        assertThat(placesReservees(vip)).isEqualTo(1);
        expirerLaPreReservation(reservationId(reponseVip));

        assertThat(placesReservees(vip)).isZero();
        assertThat(placesReservees(standard)).as("la categorie STANDARD n'a jamais ete touchee").isEqualTo(1);
    }

    @Test
    @DisplayName("(categorie stockee) Annulation d'une reservation confirmee sur VIP : SEULE VIP perd ses places, STANDARD inchangee")
    void annulation_neTouchePasLAutreCategorie() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket standard = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        CategorieTicket vip = creerCategorie(soiree, NomCategorieTicket.VIP);
        ResponseEntity<JsonNode> temoin = initierUnNumero(soiree, standard, randomPhone());
        confirmerPaiement(temoin);
        assertThat(placesReservees(standard)).isEqualTo(1);

        String telephonePayeur = randomPhone();
        ResponseEntity<JsonNode> reponseVip = initier(soiree, vip, telephonePayeur, List.of(benef(randomPhone(), "Invite")));
        confirmerPaiement(reponseVip);
        assertThat(placesReservees(vip)).isEqualTo(1);

        billetterieService.annulerReservation(reservationId(reponseVip), telephonePayeur);

        assertThat(placesReservees(vip)).isZero();
        assertThat(placesReservees(standard)).as("la categorie STANDARD n'a jamais ete touchee").isEqualTo(1);
    }

    @Test
    @DisplayName("(categorie stockee) Echec de paiement d'une reservation sur VIP : SEULE VIP perd ses places, STANDARD inchangee")
    void paiementEchoue_neTouchePasLAutreCategorie() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket standard = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        CategorieTicket vip = creerCategorie(soiree, NomCategorieTicket.VIP);
        ResponseEntity<JsonNode> temoin = initierUnNumero(soiree, standard, randomPhone());
        confirmerPaiement(temoin);
        assertThat(placesReservees(standard)).isEqualTo(1);

        ResponseEntity<JsonNode> reponseVip = initierUnNumero(soiree, vip, randomPhone());
        UUID paiementIdVip = UUID.fromString(reponseVip.getBody().get("paiementId").asText());
        assertThat(placesReservees(vip)).isEqualTo(1);

        eventPublisher.publishEvent(new PaiementEchoueEvent(paiementIdVip, TypePaiement.BILLET));

        assertThat(placesReservees(vip)).isZero();
        assertThat(placesReservees(standard)).as("la categorie STANDARD n'a jamais ete touchee").isEqualTo(1);
    }

    // ------------------------------------------------------------------ categorie stockee (suite) : verrou pessimiste
    // sur onPaiementConfirme/liberer(onPaiementEchoue)/annulerReservation — deux reservations DIFFERENTES
    // de la MEME categorie, liberees en parallele, ne doivent jamais produire de lost update sur le
    // compteur (categorieTicketRepository.findByIdForUpdate serialise les deux transactions).

    @Test
    @DisplayName("(categorie stockee) Deux echecs de paiement simultanes, MEME categorie, reservations differentes : compteur final coherent (verrou pessimiste)")
    void paiementEchoueConcurrent_compteurCoherent() throws Exception {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.VIP);
        ResponseEntity<JsonNode> r1 = initierUnNumero(soiree, categorie, randomPhone());
        ResponseEntity<JsonNode> r2 = initierUnNumero(soiree, categorie, randomPhone());
        assertThat(placesReservees(categorie)).isEqualTo(2);
        UUID paiementId1 = UUID.fromString(r1.getBody().get("paiementId").asText());
        UUID paiementId2 = UUID.fromString(r2.getBody().get("paiementId").asText());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            Callable<Void> appel1 = () -> {
                depart.await();
                billetterieService.onPaiementEchoue(new PaiementEchoueEvent(paiementId1, TypePaiement.BILLET));
                return null;
            };
            Callable<Void> appel2 = () -> {
                depart.await();
                billetterieService.onPaiementEchoue(new PaiementEchoueEvent(paiementId2, TypePaiement.BILLET));
                return null;
            };
            Future<Void> f1 = pool.submit(appel1);
            Future<Void> f2 = pool.submit(appel2);
            depart.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(placesReservees(categorie)).isZero();
    }

    @Test
    @DisplayName("(categorie stockee) Deux annulations simultanees, MEME categorie, reservations differentes : compteur final coherent (verrou pessimiste)")
    void annulationConcurrente_compteurCoherent() throws Exception {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.VIP);
        String payeur1 = randomPhone();
        String payeur2 = randomPhone();
        ResponseEntity<JsonNode> r1 = initier(soiree, categorie, payeur1, List.of(benef(randomPhone(), "A")));
        ResponseEntity<JsonNode> r2 = initier(soiree, categorie, payeur2, List.of(benef(randomPhone(), "B")));
        confirmerPaiement(r1);
        confirmerPaiement(r2);
        assertThat(placesReservees(categorie)).isEqualTo(2);
        UUID reservationId1 = reservationId(r1);
        UUID reservationId2 = reservationId(r2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            Callable<Void> appel1 = () -> {
                depart.await();
                billetterieService.annulerReservation(reservationId1, payeur1);
                return null;
            };
            Callable<Void> appel2 = () -> {
                depart.await();
                billetterieService.annulerReservation(reservationId2, payeur2);
                return null;
            };
            Future<Void> f1 = pool.submit(appel1);
            Future<Void> f2 = pool.submit(appel2);
            depart.countDown();
            f1.get(30, TimeUnit.SECONDS);
            f2.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(placesReservees(categorie)).isZero();
    }

    // ------------------------------------------------------------------ (h) telephoneMasque

    @Test
    @DisplayName("(h) GET /reservations/{id}/ticket : telephoneMasque ne contient jamais le numero complet, l'unicite est visible billet par billet")
    void telephoneMasque_neContientJamaisLeNumeroComplet() {
        SoireeEvent soiree = creerSoireeFuture();
        CategorieTicket categorie = creerCategorie(soiree, NomCategorieTicket.STANDARD);
        String t1 = randomPhone();
        String t2 = randomPhone();
        String telephonePayeur = randomPhone();
        ResponseEntity<JsonNode> reponse = initier(soiree, categorie, telephonePayeur,
                List.of(benef(t1, "Alice"), benef(t2, "Bob")));
        confirmerPaiement(reponse);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Ticket-Access-Token", reponse.getBody().get("ticketAccessToken").asText());
        // URI deja encodee (%2B) : une String serait re-encodee par le template d'URI (%252B).
        java.net.URI uri = java.net.URI.create(url("/reservations/" + reservationId(reponse) + "/ticket?telephone="
                + java.net.URLEncoder.encode(telephonePayeur, java.nio.charset.StandardCharsets.UTF_8)));
        ResponseEntity<String> brut = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        ResponseEntity<JsonNode> tickets = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

        assertThat(tickets.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tickets.getBody()).hasSize(2);
        for (JsonNode ticket : tickets.getBody()) {
            String masque = ticket.get("telephoneMasque").asText();
            assertThat(masque).isNotBlank().doesNotContain(t1).doesNotContain(t2);
            // 2 derniers chiffres seulement : ni le numero local complet (8 chiffres) ni son debut.
            assertThat(masque).doesNotContain(t1.substring(t1.length() - 8)).doesNotContain(t2.substring(t2.length() - 8));
            assertThat(masque).matches(".*(" + t1.substring(t1.length() - 2) + "|" + t2.substring(t2.length() - 2) + ")$");
        }
        // Le corps brut n'expose ni l'un ni l'autre numero complet, ni sans indicatif.
        assertThat(brut.getBody()).doesNotContain(t1).doesNotContain(t2)
                .doesNotContain(t1.substring(4)).doesNotContain(t2.substring(4));
    }
}
