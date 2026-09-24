package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.Paiement;
import bf.laterrasse.nks.domain.QRCodeTicket;
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
import bf.laterrasse.nks.dto.billetterie.TicketAvecQrResponse;
import bf.laterrasse.nks.event.PaiementConfirmeEvent;
import bf.laterrasse.nks.event.PaiementEchoueEvent;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.CategorieTicketRepository;
import bf.laterrasse.nks.repository.QRCodeTicketRepository;
import bf.laterrasse.nks.repository.ReservationBeneficiaireRepository;
import bf.laterrasse.nks.repository.ReservationRepository;
import bf.laterrasse.nks.repository.TicketRepository;
import bf.laterrasse.nks.security.TicketAccessTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (Mockito) de la regle "un numero de telephone par billet" portee par
 * BilletterieService : {@code preparerBeneficiaires} (methode privee, exercee via
 * {@code initierReservation} / {@code genererTicketsGratuits}), attribution du i-eme numero au
 * i-eme billet, et liberation des numeros (annulation, echec de paiement, cloture de soiree).
 * La contrainte d'unicite reelle en base et la concurrence sont dans
 * BeneficiairesBilletIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class BilletterieServiceBeneficiairesTest {

    @Mock private CategorieTicketRepository categorieTicketRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private QRCodeTicketRepository qrCodeTicketRepository;
    @Mock private PaiementService paiementService;
    @Mock private ParametrePlateformeService parametrePlateformeService;
    @Mock private NotificationService notificationService;
    @Mock private TicketAccessTokenService ticketAccessTokenService;
    @Mock private ReservationBeneficiaireRepository beneficiaireRepository;
    @Mock private bf.laterrasse.nks.repository.AppareilVoteSurPlaceRepository appareilRepository;

    private BilletterieService service;

    private final UUID soireeId = UUID.randomUUID();
    private final UUID categorieId = UUID.randomUUID();
    private SoireeEvent soiree;
    private CategorieTicket categorie;

    @BeforeEach
    void setUp() {
        service = new BilletterieService(categorieTicketRepository, reservationRepository, ticketRepository,
                qrCodeTicketRepository, paiementService, parametrePlateformeService, notificationService,
                ticketAccessTokenService, beneficiaireRepository, appareilRepository);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:4200");

        soiree = SoireeEvent.builder().id(soireeId).nom("Soiree test")
                .dateHeure(Instant.now().plus(3, ChronoUnit.DAYS)).build();
        categorie = CategorieTicket.builder().id(categorieId).soiree(soiree).nom(NomCategorieTicket.STANDARD)
                .prix(BigDecimal.valueOf(1000)).nbPlacesDisponibles(100).nbPlacesReservees(10).build();

        lenient().when(categorieTicketRepository.findByIdForUpdate(categorieId)).thenReturn(Optional.of(categorie));
        lenient().when(categorieTicketRepository.findBySoireeId(soireeId)).thenReturn(List.of(categorie));
        lenient().when(parametrePlateformeService.getInt(anyString(), anyInt())).thenReturn(15);
        lenient().when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        lenient().when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        lenient().when(qrCodeTicketRepository.save(any(QRCodeTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ticketAccessTokenService.emettre(anyString(), any(), any(), any())).thenReturn("jeton");
        lenient().when(ticketRepository.findTelephonesPortesParBilletActif(any(), anyCollection(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(beneficiaireRepository.findTelephonesActifs(any(), anyCollection())).thenReturn(List.of());
    }

    private static BeneficiaireBilletRequest benef(String telephone, String nom) {
        return new BeneficiaireBilletRequest(telephone, nom);
    }

    private ReservationRequest requete(int nbPlaces, List<BeneficiaireBilletRequest> beneficiaires) {
        return new ReservationRequest(soireeId, categorieId, nbPlaces, "Payeur", "70 00 00 00", null, beneficiaires);
    }

    /** Aucun effet de bord n'a eu lieu : ni places consommees, ni reservation, ni beneficiaire, ni paiement. */
    private void assertAucunEffetDeBord() {
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(10);
        verify(categorieTicketRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        verify(beneficiaireRepository, never()).saveAllAndFlush(any());
        verifyNoInteractions(paiementService);
    }

    // ------------------------------------------------------------------ validation (preparerBeneficiaires)

    @Test
    @DisplayName("Taille de beneficiaires != nbPlaces (trop peu, trop, null) => ValidationMetierException, aucun effet de bord")
    void tailleDifferenteDeNbPlaces_estRefusee() {
        assertThatThrownBy(() -> service.initierReservation(requete(2, List.of(benef("70000001", null)))))
                .isInstanceOf(ValidationMetierException.class);
        assertThatThrownBy(() -> service.initierReservation(requete(1,
                List.of(benef("70000001", null), benef("70000002", null)))))
                .isInstanceOf(ValidationMetierException.class);
        assertThatThrownBy(() -> service.initierReservation(requete(1, null)))
                .isInstanceOf(ValidationMetierException.class);

        assertAucunEffetDeBord();
    }

    @Test
    @DisplayName("Doublon interne apres normalisation (0022670.. / +22670.. / espaces / local) => refuse, aucun effet de bord")
    void doublonInterne_apresNormalisation_estRefuse() {
        String[][] paires = {
                {"0022670000001", "+22670000001"},
                {"70 00 00 01", "+22670000001"},
                {"70-00-00-01", "22670000001"},
                {"70000001", "0022670000001"},
        };
        for (String[] paire : paires) {
            assertThatThrownBy(() -> service.initierReservation(
                    requete(2, List.of(benef(paire[0], "A"), benef(paire[1], "B")))))
                    .as("paire %s / %s", paire[0], paire[1])
                    .isInstanceOf(ValidationMetierException.class)
                    .hasMessageContaining("plusieurs fois")
                    .hasMessageNotContaining("+22670000001");
        }
        assertAucunEffetDeBord();
    }

    @Test
    @DisplayName("Numero non E.164 (lettres, trop court, commence par 0 apres +, null, entree nulle) => refuse")
    void numeroNonE164_estRefuse() {
        String[] invalides = {"abcdefgh", "12", "+0123456789", "+22670 00 00 0x", "+2267000000123456789"};
        for (String invalide : invalides) {
            assertThatThrownBy(() -> service.initierReservation(requete(1, List.of(benef(invalide, null)))))
                    .as("numero %s", invalide)
                    .isInstanceOf(ValidationMetierException.class)
                    .hasMessageContaining("invalide");
        }
        assertThatThrownBy(() -> service.initierReservation(requete(1, List.of(benef(null, null)))))
                .isInstanceOf(ValidationMetierException.class);
        assertThatThrownBy(() -> service.initierReservation(requete(1, java.util.Collections.singletonList(null))))
                .isInstanceOf(ValidationMetierException.class);

        assertAucunEffetDeBord();
    }

    @Test
    @DisplayName("Numero deja porte par un billet ACTIF (legacy compris) => ConflitEtatException sans fuite de places ni reveler le numero complet")
    void numeroDejaSurUnBilletActif_estRefuse() {
        when(ticketRepository.findTelephonesPortesParBilletActif(eq(soireeId), anyCollection(), any()))
                .thenReturn(List.of("+22670000002"));

        assertThatThrownBy(() -> service.initierReservation(requete(2,
                List.of(benef("70000001", null), benef("70000002", null)))))
                .isInstanceOf(ConflitEtatException.class)
                .hasMessageContaining("déjà utilisé")
                .hasMessageNotContaining("+22670000002");

        assertAucunEffetDeBord();
    }

    @Test
    @DisplayName("La requete d'unicite des billets exclut bien ANNULE et EXPIRE")
    void requeteBilletsActifs_exclutAnnuleEtExpire() {
        Paiement paiement = new Paiement();
        paiement.setId(UUID.randomUUID());
        when(paiementService.creerEtDemarrer(any(), any(), anyString(), any()))
                .thenReturn(new PaiementInitie(paiement, "https://pay.local"));

        service.initierReservation(requete(1, List.of(benef("70000001", null))));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Collection<StatutTicket>> statuts = ArgumentCaptor.forClass((Class) Collection.class);
        verify(ticketRepository).findTelephonesPortesParBilletActif(eq(soireeId), anyCollection(), statuts.capture());
        assertThat(statuts.getValue()).containsExactlyInAnyOrder(StatutTicket.ANNULE, StatutTicket.EXPIRE);
    }

    @Test
    @DisplayName("Numero deja porte par un beneficiaire ACTIF (pre-reservation PENDING) => ConflitEtatException sans fuite de places")
    void numeroDejaBeneficiaireActif_estRefuse() {
        when(beneficiaireRepository.findTelephonesActifs(eq(soireeId), anyCollection()))
                .thenReturn(List.of("+22670000001"));

        assertThatThrownBy(() -> service.initierReservation(requete(1, List.of(benef("70000001", null)))))
                .isInstanceOf(ConflitEtatException.class)
                .hasMessageContaining("déjà utilisé");

        assertAucunEffetDeBord();
    }

    // ------------------------------------------------------------------ cas nominal

    @Test
    @DisplayName("2 beneficiaires distincts : reservation PENDING, places pre-reservees, beneficiaires normalises positions 0 et 1")
    void deuxBeneficiairesDistincts_reservationCreee() {
        Paiement paiement = new Paiement();
        paiement.setId(UUID.randomUUID());
        when(paiementService.creerEtDemarrer(eq(TypePaiement.BILLET), any(), anyString(), any()))
                .thenReturn(new PaiementInitie(paiement, "https://pay.local"));

        var reponse = service.initierReservation(requete(2,
                List.of(benef("0022670000001", "  Alice  "), benef("70 00 00 02", " "))));

        assertThat(reponse.statut()).isEqualTo("PENDING");
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(12);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<ReservationBeneficiaire>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(beneficiaireRepository).saveAllAndFlush(captor.capture());
        List<ReservationBeneficiaire> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(ReservationBeneficiaire::getTelephone)
                .containsExactly("+22670000001", "+22670000002");
        assertThat(saved).extracting(ReservationBeneficiaire::getPosition).containsExactly((short) 0, (short) 1);
        assertThat(saved).extracting(ReservationBeneficiaire::getNom).containsExactly("Alice", null);
        assertThat(saved).allMatch(ReservationBeneficiaire::isActif);
        assertThat(saved).allMatch(b -> soireeId.equals(b.getSoireeId()) && b.getReservationId() != null);
    }

    @Test
    @DisplayName("Echec d'initialisation du paiement : les places pre-reservees sont rendues")
    void echecPaiement_libereLesPlaces() {
        when(paiementService.creerEtDemarrer(any(), any(), anyString(), any()))
                .thenThrow(new RuntimeException("gateway KO"));

        assertThatThrownBy(() -> service.initierReservation(requete(1, List.of(benef("70000001", null)))))
                .isInstanceOf(ValidationMetierException.class);

        assertThat(categorie.getNbPlacesReservees()).isEqualTo(10);
    }

    // ------------------------------------------------------------------ genererTickets / gratuits

    @Test
    @DisplayName("genererTicketsGratuits : le i-eme billet porte le telephone et le nom du i-eme beneficiaire")
    void ticketsGratuits_iemeBilletPorteIemeBeneficiaire() {
        // Simule la base : ce qui est persiste par saveAllAndFlush est relu (ordre "position") par genererTickets.
        List<ReservationBeneficiaire> persistes = new ArrayList<>();
        when(beneficiaireRepository.saveAllAndFlush(any())).thenAnswer(inv -> {
            Collection<ReservationBeneficiaire> entites = inv.getArgument(0);
            persistes.addAll(entites);
            return List.copyOf(entites);
        });
        when(beneficiaireRepository.findByReservationIdOrderByPositionAsc(any())).thenAnswer(inv -> persistes);

        Reservation reservation = service.genererTicketsGratuits(soireeId, categorieId, "Admin Client", "70000000", 3,
                List.of(benef("70000001", "Alice"), benef("70000002", null), benef("0022670000003", "Carl")), null);

        ArgumentCaptor<Ticket> tickets = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, times(3)).save(tickets.capture());
        assertThat(tickets.getAllValues()).extracting(Ticket::getTelephoneSpectateur)
                .containsExactly("+22670000001", "+22670000002", "+22670000003");
        // Sans nom de beneficiaire : repli sur le nom du reservant.
        assertThat(tickets.getAllValues()).extracting(Ticket::getNomSpectateur)
                .containsExactly("Alice", "Admin Client", "Carl");
        assertThat(reservation.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(13);
    }

    @Test
    @DisplayName("genererTicketsGratuits : memes regles (taille, doublon, numero deja pris) que la reservation publique, sans effet de bord")
    void ticketsGratuits_memesRegles() {
        assertThatThrownBy(() -> service.genererTicketsGratuits(soireeId, categorieId, "N", "70000000", 2,
                List.of(benef("70000001", null)), null)).isInstanceOf(ValidationMetierException.class);
        assertThatThrownBy(() -> service.genererTicketsGratuits(soireeId, categorieId, "N", "70000000", 2,
                List.of(benef("70000001", null), benef("+22670000001", null)), null))
                .isInstanceOf(ValidationMetierException.class);

        when(beneficiaireRepository.findTelephonesActifs(eq(soireeId), anyCollection()))
                .thenReturn(List.of("+22670000001"));
        assertThatThrownBy(() -> service.genererTicketsGratuits(soireeId, categorieId, "N", "70000000", 1,
                List.of(benef("70000001", null)), null)).isInstanceOf(ConflitEtatException.class);

        assertAucunEffetDeBord();
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Confirmation de paiement d'une reservation legacy (sans beneficiaire) : numero du reservant conserve")
    void confirmationLegacy_utiliseLeNumeroDuReservant() {
        Reservation legacy = Reservation.builder().id(UUID.randomUUID()).soiree(soiree)
                .telephoneReservant("+22670000009").nomReservant("Legacy").nbPlaces(2)
                .statut(StatutReservation.PENDING).build();
        UUID paiementId = UUID.randomUUID();
        when(reservationRepository.findByPaiementId(paiementId)).thenReturn(Optional.of(legacy));
        when(beneficiaireRepository.findByReservationIdOrderByPositionAsc(legacy.getId())).thenReturn(List.of());

        service.onPaiementConfirme(new PaiementConfirmeEvent(paiementId, TypePaiement.BILLET, null,
                BigDecimal.TEN, "ref", "+22670000009"));

        ArgumentCaptor<Ticket> tickets = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, times(2)).save(tickets.capture());
        assertThat(tickets.getAllValues()).extracting(Ticket::getTelephoneSpectateur)
                .containsOnly("+22670000009");
    }

    // ------------------------------------------------------------------ liberation des numeros

    @Test
    @DisplayName("Paiement echoue : beneficiaires desactives et places rendues")
    void paiementEchoue_liberreNumerosEtPlaces() {
        Reservation pending = Reservation.builder().id(UUID.randomUUID()).soiree(soiree).nbPlaces(2)
                .statut(StatutReservation.PENDING).build();
        UUID paiementId = UUID.randomUUID();
        when(reservationRepository.findByPaiementId(paiementId)).thenReturn(Optional.of(pending));

        service.onPaiementEchoue(new PaiementEchoueEvent(paiementId, TypePaiement.BILLET));

        assertThat(pending.getStatut()).isEqualTo(StatutReservation.EXPIREE);
        verify(beneficiaireRepository).desactiverParReservation(pending.getId());
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(8);
    }

    @Test
    @DisplayName("Annulation d'une reservation confirmee : beneficiaires desactives, billets ANNULE, places rendues")
    void annulation_liberreLesNumeros() {
        Reservation confirmee = Reservation.builder().id(UUID.randomUUID()).soiree(soiree)
                .telephoneReservant("+22670000000").nbPlaces(1).statut(StatutReservation.CONFIRMEE).build();
        when(reservationRepository.findById(confirmee.getId())).thenReturn(Optional.of(confirmee));
        when(ticketRepository.findByReservationId(confirmee.getId())).thenReturn(new ArrayList<>());

        service.annulerReservation(confirmee.getId(), "70000000");

        assertThat(confirmee.getStatut()).isEqualTo(StatutReservation.ANNULEE);
        verify(beneficiaireRepository).desactiverParReservation(confirmee.getId());
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(9);
    }

    @Test
    @DisplayName("Cloture de soiree : chaque billet EMIS expire libere le numero de SON beneficiaire uniquement")
    void clotureSoiree_liberreLeNumeroDeChaqueBilletExpire() {
        Reservation reservation = Reservation.builder().id(UUID.randomUUID()).soiree(soiree).build();
        Ticket t1 = Ticket.builder().id(UUID.randomUUID()).reservation(reservation).soiree(soiree)
                .telephoneSpectateur("+22670000001").statut(StatutTicket.EMIS).build();
        Ticket t2 = Ticket.builder().id(UUID.randomUUID()).reservation(reservation).soiree(soiree)
                .telephoneSpectateur("+22670000002").statut(StatutTicket.EMIS).build();
        when(ticketRepository.findBySoireeIdAndStatut(soireeId, StatutTicket.EMIS)).thenReturn(List.of(t1, t2));

        service.expirerTicketsSoiree(soireeId);

        assertThat(t1.getStatut()).isEqualTo(StatutTicket.EXPIRE);
        assertThat(t2.getStatut()).isEqualTo(StatutTicket.EXPIRE);
        verify(beneficiaireRepository).desactiverParReservationEtTelephone(reservation.getId(), "+22670000001");
        verify(beneficiaireRepository).desactiverParReservationEtTelephone(reservation.getId(), "+22670000002");
        verify(beneficiaireRepository, times(2)).desactiverParReservationEtTelephone(any(), anyString());
    }

    @Test
    @DisplayName("Cloture de soiree sans billet EMIS : aucune liberation")
    void clotureSoiree_sansBilletEmis_neFaitRien() {
        when(ticketRepository.findBySoireeIdAndStatut(soireeId, StatutTicket.EMIS)).thenReturn(List.of());

        service.expirerTicketsSoiree(soireeId);

        verifyNoInteractions(beneficiaireRepository);
    }

    // ------------------------------------------------------------------ audit : messages, plafonds, annulation

    @Test
    @DisplayName("(3) Message d'unicite generique : ne nomme aucun numero, meme masque, et n'expose ni billet ni porteur")
    void messageUnicite_estGenerique() {
        when(ticketRepository.findTelephonesPortesParBilletActif(eq(soireeId), anyCollection(), any()))
                .thenReturn(List.of("+22670004512"));

        assertThatThrownBy(() -> service.initierReservation(requete(1, List.of(benef("70004512", null)))))
                .isInstanceOf(ConflitEtatException.class)
                .hasMessageContaining("L'un des numéros saisis est déjà utilisé pour un billet de cette soirée")
                .hasMessageNotContaining("4512").hasMessageNotContaining("12").hasMessageNotContaining("•");
    }

    @Test
    @DisplayName("(3) Doublon interne : message sans aucun numero")
    void messageDoublonInterne_neNommeAucunNumero() {
        assertThatThrownBy(() -> service.initierReservation(requete(2,
                List.of(benef("70004512", "A"), benef("+22670004512", "B")))))
                .isInstanceOf(ValidationMetierException.class)
                .hasMessageContaining("plusieurs fois")
                .hasMessageNotContaining("4512").hasMessageNotContaining("•");
    }

    @Test
    @DisplayName("(2) Payeur avec deja 2 pre-reservations PENDING non expirees pour la soiree => 409, aucun effet de bord")
    void plafondPreReservationsEnAttente_estRefuse() {
        when(reservationRepository.countByTelephoneReservantAndSoireeIdAndStatutAndDateExpirationAfter(
                eq("+22670000000"), eq(soireeId), eq(StatutReservation.PENDING), any(Instant.class))).thenReturn(2L);

        assertThatThrownBy(() -> service.initierReservation(requete(1, List.of(benef("70000001", null)))))
                .isInstanceOf(ConflitEtatException.class)
                .hasMessageContaining("2 réservations en attente");

        assertAucunEffetDeBord();
    }

    @Test
    @DisplayName("(2) Payeur avec 1 seule pre-reservation en attente : accepte")
    void unePreReservationEnAttente_estAcceptee() {
        when(reservationRepository.countByTelephoneReservantAndSoireeIdAndStatutAndDateExpirationAfter(
                eq("+22670000000"), eq(soireeId), eq(StatutReservation.PENDING), any(Instant.class))).thenReturn(1L);
        Paiement paiement = new Paiement();
        paiement.setId(UUID.randomUUID());
        when(paiementService.creerEtDemarrer(any(), any(), anyString(), any()))
                .thenReturn(new PaiementInitie(paiement, "https://pay.local"));

        assertThat(service.initierReservation(requete(1, List.of(benef("70000001", null)))).statut())
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("(2) Plafonds serveur des DTO : nbPlaces/beneficiaires <= 10 (public), <= 50 (admin), longueurs bornees")
    void plafondsBeanValidation() {
        jakarta.validation.Validator validator =
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        List<BeneficiaireBilletRequest> onze = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            onze.add(benef("7000000" + i, null));
        }
        assertThat(validator.validate(new ReservationRequest(soireeId, categorieId, 11, "P", "70000000", null, onze)))
                .isNotEmpty();
        assertThat(validator.validate(new ReservationRequest(soireeId, categorieId, 10, "P", "70000000", null,
                onze.subList(0, 10)))).isEmpty();
        assertThat(validator.validate(new ReservationRequest(soireeId, categorieId, 1, "P".repeat(151), "70000000",
                null, onze.subList(0, 1)))).isNotEmpty();
        assertThat(validator.validate(new ReservationRequest(soireeId, categorieId, 1, "P", "7".repeat(31), null,
                onze.subList(0, 1)))).isNotEmpty();
        assertThat(validator.validate(new ReservationRequest(soireeId, categorieId, 1, "P", "70000000", null,
                List.of(benef("7".repeat(31), null))))).isNotEmpty();
        assertThat(validator.validate(new ReservationRequest(soireeId, categorieId, 1, "P", "70000000", null,
                List.of(benef("70000001", "N".repeat(151)))))).isNotEmpty();

        List<BeneficiaireBilletRequest> cinquante = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            cinquante.add(benef("7000" + (1000 + i), null));
        }
        assertThat(validator.validate(new bf.laterrasse.nks.dto.billetterie.TicketsGratuitsRequest(
                soireeId, categorieId, "N", "70000000", 50, cinquante.subList(0, 50)))).isEmpty();
        assertThat(validator.validate(new bf.laterrasse.nks.dto.billetterie.TicketsGratuitsRequest(
                soireeId, categorieId, "N", "70000000", 51, cinquante))).isNotEmpty();
    }

    @Test
    @DisplayName("(6) telephoneVotant : seuls chiffres + . - espace, 30 caracteres max")
    void telephoneVotantValide() {
        jakarta.validation.Validator validator =
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        UUID c = UUID.randomUUID();
        assertThat(validator.validate(new bf.laterrasse.nks.dto.votesurplace.VoterSurPlaceRequest(
                c, "+226 70-00.00 01", null, null, null))).isEmpty();
        assertThat(validator.validate(new bf.laterrasse.nks.dto.votesurplace.VoterSurPlaceRequest(
                c, null, null, null, null))).isEmpty();
        assertThat(validator.validate(new bf.laterrasse.nks.dto.votesurplace.VoterSurPlaceRequest(
                c, "<script>", null, null, null))).isNotEmpty();
        assertThat(validator.validate(new bf.laterrasse.nks.dto.votesurplace.VoterSurPlaceRequest(
                c, "7".repeat(31), null, null, null))).isNotEmpty();
    }

    @Test
    @DisplayName("(5) Annulation refusee si un billet de la reservation est UTILISE : aucune modification")
    void annulation_refuseeSiUnBilletEstUtilise() {
        Reservation confirmee = Reservation.builder().id(UUID.randomUUID()).soiree(soiree)
                .telephoneReservant("+22670000000").nbPlaces(2).statut(StatutReservation.CONFIRMEE).build();
        Ticket emis = Ticket.builder().id(UUID.randomUUID()).reservation(confirmee).soiree(soiree)
                .telephoneSpectateur("+22670000001").statut(StatutTicket.EMIS).build();
        Ticket utilise = Ticket.builder().id(UUID.randomUUID()).reservation(confirmee).soiree(soiree)
                .telephoneSpectateur("+22670000002").statut(StatutTicket.UTILISE).build();
        when(reservationRepository.findById(confirmee.getId())).thenReturn(Optional.of(confirmee));
        when(ticketRepository.findByReservationId(confirmee.getId())).thenReturn(new ArrayList<>(List.of(emis, utilise)));

        assertThatThrownBy(() -> service.annulerReservation(confirmee.getId(), "70000000"))
                .isInstanceOf(ConflitEtatException.class)
                .hasMessageContaining("déjà été utilisé");

        assertThat(confirmee.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        assertThat(emis.getStatut()).isEqualTo(StatutTicket.EMIS);
        assertThat(utilise.getStatut()).isEqualTo(StatutTicket.UTILISE);
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(10);
        verify(reservationRepository, never()).save(any());
        verify(ticketRepository, never()).saveAll(any());
        verifyNoInteractions(beneficiaireRepository, appareilRepository);
    }

    @Test
    @DisplayName("(9) Annulation : les appareils de vote des billets annules sont supprimes")
    void annulation_supprimeLesAppareilsDesBilletsAnnules() {
        Reservation confirmee = Reservation.builder().id(UUID.randomUUID()).soiree(soiree)
                .telephoneReservant("+22670000000").nbPlaces(2).statut(StatutReservation.CONFIRMEE).build();
        Ticket t1 = Ticket.builder().id(UUID.randomUUID()).reservation(confirmee).soiree(soiree)
                .telephoneSpectateur("+22670000001").statut(StatutTicket.EMIS).build();
        Ticket t2 = Ticket.builder().id(UUID.randomUUID()).reservation(confirmee).soiree(soiree)
                .telephoneSpectateur("+22670000002").statut(StatutTicket.EMIS).build();
        when(reservationRepository.findById(confirmee.getId())).thenReturn(Optional.of(confirmee));
        when(ticketRepository.findByReservationId(confirmee.getId())).thenReturn(new ArrayList<>(List.of(t1, t2)));

        service.annulerReservation(confirmee.getId(), "70000000");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass((Class) Collection.class);
        verify(appareilRepository).supprimerParTicketIds(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(t1.getId(), t2.getId());
    }

    @Test
    @DisplayName("(9) Cloture de soiree : appareils supprimes pour les billets expires, JAMAIS pour un billet UTILISE")
    void clotureSoiree_supprimeLesAppareils_saufBilletUtilise() {
        Reservation reservation = Reservation.builder().id(UUID.randomUUID()).soiree(soiree).build();
        Ticket emis = Ticket.builder().id(UUID.randomUUID()).reservation(reservation).soiree(soiree)
                .telephoneSpectateur("+22670000001").statut(StatutTicket.EMIS).build();
        Ticket utilise = Ticket.builder().id(UUID.randomUUID()).reservation(reservation).soiree(soiree)
                .telephoneSpectateur("+22670000002").statut(StatutTicket.UTILISE).build();
        // Le repository ne renvoie normalement que des EMIS ; on force un UTILISE pour verifier le filtre defensif.
        when(ticketRepository.findBySoireeIdAndStatut(soireeId, StatutTicket.EMIS)).thenReturn(List.of(emis, utilise));

        service.expirerTicketsSoiree(soireeId);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Collection<UUID>> ids = ArgumentCaptor.forClass((Class) Collection.class);
        verify(appareilRepository).supprimerParTicketIds(ids.capture());
        assertThat(ids.getValue()).containsExactly(emis.getId());
    }

    // ------------------------------------------------------------------ audit : paiement tardif (reservation EXPIREE)

    private Reservation reservationExpireePayee(int nbPlaces, String... telephones) {
        Reservation r = Reservation.builder().id(UUID.randomUUID()).soiree(soiree).telephoneReservant("+22670000000")
                .nomReservant("Payeur").nbPlaces(nbPlaces).statut(StatutReservation.EXPIREE).build();
        List<ReservationBeneficiaire> lignes = new ArrayList<>();
        for (int i = 0; i < telephones.length; i++) {
            lignes.add(ReservationBeneficiaire.builder().reservationId(r.getId()).soireeId(soireeId)
                    .position((short) i).telephone(telephones[i]).actif(false).build());
        }
        lenient().when(beneficiaireRepository.findByReservationIdOrderByPositionAsc(r.getId())).thenReturn(lignes);
        return r;
    }

    private PaiementConfirmeEvent paiementConfirme(Reservation r) {
        UUID paiementId = UUID.randomUUID();
        when(reservationRepository.findByPaiementId(paiementId)).thenReturn(Optional.of(r));
        return new PaiementConfirmeEvent(paiementId, TypePaiement.BILLET, null, BigDecimal.TEN, "ref", "+22670000000");
    }

    @Test
    @DisplayName("(1) Paiement tardif, tout est libre : places re-reservees, lignes reactivees, billets emis, reservation CONFIRMEE")
    void paiementTardif_toutLibre_reactiveEtEmet() {
        Reservation r = reservationExpireePayee(2, "+22670000001", "+22670000002");
        when(beneficiaireRepository.reactiverParReservation(r.getId())).thenReturn(2);

        service.onPaiementConfirme(paiementConfirme(r));

        verify(beneficiaireRepository).reactiverParReservation(r.getId());
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(12);
        assertThat(r.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        ArgumentCaptor<Ticket> tickets = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, times(2)).save(tickets.capture());
        assertThat(tickets.getAllValues()).extracting(Ticket::getTelephoneSpectateur)
                .containsExactly("+22670000001", "+22670000002");
        // La re-verification exclut bien la propre reservation.
        verify(ticketRepository).findTelephonesPortesParBilletActifHorsReservation(
                eq(soireeId), anyCollection(), anyCollection(), eq(r.getId()));
        verify(beneficiaireRepository).findTelephonesActifsHorsReservation(eq(soireeId), anyCollection(), eq(r.getId()));
        verify(beneficiaireRepository).verrouillerSoiree(anyString());
    }

    @Test
    @DisplayName("(1) Paiement tardif, numero repris par un autre beneficiaire actif : aucun billet, reste EXPIREE, aucune exception, SMS au payeur")
    void paiementTardif_numeroRepris_parBeneficiaire_neEmetPas() {
        Reservation r = reservationExpireePayee(2, "+22670000001", "+22670000002");
        when(beneficiaireRepository.findTelephonesActifsHorsReservation(eq(soireeId), anyCollection(), eq(r.getId())))
                .thenReturn(List.of("+22670000002"));

        org.assertj.core.api.Assertions.assertThatCode(() -> service.onPaiementConfirme(paiementConfirme(r)))
                .doesNotThrowAnyException();

        assertThat(r.getStatut()).isEqualTo(StatutReservation.EXPIREE);
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(10);
        verify(ticketRepository, never()).save(any());
        verify(beneficiaireRepository, never()).reactiverParReservation(any());
        verify(notificationService).envoyerSms(any(), eq("+22670000000"), any(), anyString());
    }

    @Test
    @DisplayName("(1) Paiement tardif, numero repris par un billet actif : aucun billet, reste EXPIREE")
    void paiementTardif_numeroRepris_parBillet_neEmetPas() {
        Reservation r = reservationExpireePayee(1, "+22670000001");
        when(ticketRepository.findTelephonesPortesParBilletActifHorsReservation(
                eq(soireeId), anyCollection(), anyCollection(), eq(r.getId()))).thenReturn(List.of("+22670000001"));

        service.onPaiementConfirme(paiementConfirme(r));

        assertThat(r.getStatut()).isEqualTo(StatutReservation.EXPIREE);
        verify(ticketRepository, never()).save(any());
        verify(beneficiaireRepository, never()).reactiverParReservation(any());
    }

    @Test
    @DisplayName("(1) Paiement tardif, plus assez de places : aucun billet, reste EXPIREE, compteur intact")
    void paiementTardif_placesEpuisees_neEmetPas() {
        categorie.setNbPlacesReservees(99);
        Reservation r = reservationExpireePayee(2, "+22670000001", "+22670000002");

        service.onPaiementConfirme(paiementConfirme(r));

        assertThat(r.getStatut()).isEqualTo(StatutReservation.EXPIREE);
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(99);
        verify(ticketRepository, never()).save(any());
        verify(beneficiaireRepository, never()).reactiverParReservation(any());
    }

    @Test
    @DisplayName("(1) Paiement tardif, reactivation partielle (garde NOT EXISTS) : tout est redesactive, aucun billet")
    void paiementTardif_reactivationPartielle_annuleTout() {
        Reservation r = reservationExpireePayee(2, "+22670000001", "+22670000002");
        when(beneficiaireRepository.reactiverParReservation(r.getId())).thenReturn(1);

        service.onPaiementConfirme(paiementConfirme(r));

        verify(beneficiaireRepository).desactiverParReservation(r.getId());
        assertThat(r.getStatut()).isEqualTo(StatutReservation.EXPIREE);
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(10);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("(1) Paiement tardif : une erreur (ex. DataIntegrityViolationException) ne remonte JAMAIS du listener")
    void paiementTardif_erreurDeBase_neRemonteJamais() {
        Reservation r = reservationExpireePayee(1, "+22670000001");
        when(beneficiaireRepository.reactiverParReservation(r.getId()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Key (soiree_id, telephone)=(x, +22670000001)"));

        org.assertj.core.api.Assertions.assertThatCode(() -> service.onPaiementConfirme(paiementConfirme(r)))
                .doesNotThrowAnyException();

        assertThat(r.getStatut()).isEqualTo(StatutReservation.EXPIREE);
        verify(ticketRepository, never()).save(any());
    }

    @Test
    @DisplayName("(1) Paiement sur une reservation PENDING normale : aucune re-verification, comportement inchange")
    void paiementNormal_neDeclenchePasLaReverification() {
        Reservation pending = Reservation.builder().id(UUID.randomUUID()).soiree(soiree)
                .telephoneReservant("+22670000009").nomReservant("N").nbPlaces(1).statut(StatutReservation.PENDING).build();
        when(beneficiaireRepository.findByReservationIdOrderByPositionAsc(pending.getId())).thenReturn(List.of());

        service.onPaiementConfirme(paiementConfirme(pending));

        assertThat(pending.getStatut()).isEqualTo(StatutReservation.CONFIRMEE);
        verify(beneficiaireRepository, never()).reactiverParReservation(any());
        verify(ticketRepository, never()).findTelephonesPortesParBilletActifHorsReservation(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ masquage

    @Test
    @DisplayName("masquerTelephone : jamais plus de 2 chiffres en clair, jamais le numero complet")
    void masquerTelephone_neRevelePasLeNumero() {
        String masque = TicketAvecQrResponse.masquerTelephone("+22670004512");
        assertThat(masque).isEqualTo("+226 •• •• •• 12");
        assertThat(masque).doesNotContain("70004512").doesNotContain("7000");

        assertThat(TicketAvecQrResponse.masquerTelephone("+33612345678")).doesNotContain("3361234");
        assertThat(TicketAvecQrResponse.masquerTelephone(null)).isEmpty();
        assertThat(TicketAvecQrResponse.masquerTelephone("12")).doesNotContain("12");
    }
}
