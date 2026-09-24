package bf.laterrasse.nks.integration.billetterie;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.dto.billetterie.TicketAvecQrResponse;
import bf.laterrasse.nks.exception.ApiError;
import bf.laterrasse.nks.integration.AbstractIntegrationTest;
import bf.laterrasse.nks.security.TicketAccessTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for GAP-03: GET /reservations/{reservationId}/ticket?telephone=.
 * Exercised through a real HTTP call against the full Spring context (security filter
 * chain included) and a real PostgreSQL, since the whole point of this endpoint is that it
 * exposes a real secret (qrUuid) and must not leak the existence of a reservation via its
 * error responses.
 */
class ReservationTicketEndpointTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TicketAccessTokenService ticketAccessTokenService;

    @LocalServerPort
    private int port;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1" + path;
    }

    private SoireeEvent creerSoireeAvecCategorie() {
        Edition edition = creerEdition();
        Phase phase = creerPhase(edition);
        SoireeEvent soiree = creerSoiree(edition, phase, false);
        creerCategorieTicket(soiree);
        return soiree;
    }

    @Test
    @DisplayName("Correct phone number returns the tickets with their real qrUuid")
    void bonTelephone_retourneLesTicketsAvecLeurVraiQrUuid() {
        SoireeEvent soiree = creerSoireeAvecCategorie();
        CategorieTicket categorie = categorieTicketRepository.findBySoireeId(soiree.getId()).get(0);
        String telephone = randomPhone();
        Reservation reservation = creerReservation(soiree, telephone, 1);
        QRCodeTicket qr = creerBilletComplet(soiree, categorie, reservation, telephone);

        ResponseEntity<TicketAvecQrResponse[]> response = getAvecJeton(
                "/reservations/" + reservation.getId() + "/ticket", telephone, telephone,
                TicketAvecQrResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].qrUuid()).isEqualTo(qr.getCodeUuid());
        assertThat(response.getBody()[0].ticketId()).isEqualTo(qr.getTicket().getId());
    }

    @Test
    @DisplayName("nbPlaces > 1 returns every ticket of the reservation, each with its own qrUuid")
    void plusieursPlaces_retourneTousLesTickets() {
        SoireeEvent soiree = creerSoireeAvecCategorie();
        CategorieTicket categorie = categorieTicketRepository.findBySoireeId(soiree.getId()).get(0);
        String telephone = randomPhone();
        Reservation reservation = creerReservation(soiree, telephone, 3);
        QRCodeTicket qr1 = creerBilletComplet(soiree, categorie, reservation, telephone);
        QRCodeTicket qr2 = creerBilletComplet(soiree, categorie, reservation, telephone);
        QRCodeTicket qr3 = creerBilletComplet(soiree, categorie, reservation, telephone);

        ResponseEntity<TicketAvecQrResponse[]> response = getAvecJeton(
                "/reservations/" + reservation.getId() + "/ticket", telephone, telephone,
                TicketAvecQrResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> qrUuidsRetournes = List.of(response.getBody()).stream().map(TicketAvecQrResponse::qrUuid).toList();
        assertThat(qrUuidsRetournes).containsExactlyInAnyOrder(qr1.getCodeUuid(), qr2.getCodeUuid(), qr3.getCodeUuid());
    }

    @Test
    @DisplayName("Wrong phone number and a non-existent reservationId return the exact same error (no existence oracle)")
    void mauvaisTelephoneOuReservationInexistante_retournentLaMemeErreur() {
        SoireeEvent soiree = creerSoireeAvecCategorie();
        CategorieTicket categorie = categorieTicketRepository.findBySoireeId(soiree.getId()).get(0);
        String bonTelephone = randomPhone();
        Reservation reservation = creerReservation(soiree, bonTelephone, 1);
        creerBilletComplet(soiree, categorie, reservation, bonTelephone);

        // Jetons phone-wide valides (comme apres l'OTP) pour que la requete atteigne bien la verification
        // metier du service : c'est elle qui ne doit pas servir d'oracle d'existence.
        String mauvaisTelephone = randomPhone();
        ResponseEntity<ApiError> reponseMauvaisTelephone = getAvecJeton(
                "/reservations/" + reservation.getId() + "/ticket", mauvaisTelephone, mauvaisTelephone,
                ApiError.class);

        ResponseEntity<ApiError> reponseReservationInexistante = getAvecJeton(
                "/reservations/" + UUID.randomUUID() + "/ticket", bonTelephone, bonTelephone,
                ApiError.class);

        assertThat(reponseMauvaisTelephone.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reponseReservationInexistante.getStatusCode()).isEqualTo(reponseMauvaisTelephone.getStatusCode());

        assertThat(reponseMauvaisTelephone.getBody()).isNotNull();
        assertThat(reponseReservationInexistante.getBody()).isNotNull();
        assertThat(reponseReservationInexistante.getBody().code()).isEqualTo(reponseMauvaisTelephone.getBody().code());
        assertThat(reponseReservationInexistante.getBody().message()).isEqualTo(reponseMauvaisTelephone.getBody().message());
    }

    /**
     * GET /reservations/{id}/ticket exige desormais un jeton d'acces billets (correctif IDOR) : on emet ici un
     * jeton phone-wide "read" pour {@code telephoneDuJeton}. L'URI est passee deja encodee (%2B) pour eviter
     * le double encodage par les URI templates de RestTemplate.
     */
    private <T> ResponseEntity<T> getAvecJeton(String path, String telephoneDuJeton, String telephoneRequete,
                                               Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Ticket-Access-Token", ticketAccessTokenService.emettre(
                telephoneDuJeton, Set.of("read"), null, Duration.ofMinutes(5)));
        return restTemplate.exchange(URI.create(url(path + "?telephone=" + enc(telephoneRequete))),
                HttpMethod.GET, new HttpEntity<>(headers), type);
    }

    private static String enc(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
