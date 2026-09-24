package bf.laterrasse.nks.integration;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.Poule;
import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.Role;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.NomCategorieTicket;
import bf.laterrasse.nks.domain.enums.Enums.NomPhase;
import bf.laterrasse.nks.domain.enums.Enums.RoleName;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.CategorieTicketRepository;
import bf.laterrasse.nks.repository.DroitVoteSurPlaceRepository;
import bf.laterrasse.nks.repository.EditionRepository;
import bf.laterrasse.nks.repository.PhaseRepository;
import bf.laterrasse.nks.repository.PouleRepository;
import bf.laterrasse.nks.repository.QRCodeTicketRepository;
import bf.laterrasse.nks.repository.ReservationRepository;
import bf.laterrasse.nks.repository.RoleRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.repository.TicketRepository;
import bf.laterrasse.nks.repository.UtilisateurRepository;
import bf.laterrasse.nks.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base commune des tests d'integration du module vote sur place / billetterie : demarre un
 * vrai PostgreSQL (Testcontainers, conteneur unique partage pour toute la JVM de test) sur
 * lequel Flyway applique le schema reel, y compris V13/V14 (contrainte UNIQUE ticket_id,
 * role HOTESSE). Sans ce conteneur reel, la contrainte UNIQUE ne serait jamais exercee.
 *
 * Volontairement PAS de @Transactional sur les sous-classes : les tests de concurrence ont
 * besoin que chaque appel commite reellement pour etre visible depuis les autres threads.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nks_test")
            .withUsername("nks_test")
            .withPassword("nks_test");

    // Conteneur SINGLETON demarre une seule fois pour toute la JVM de test (arrete par Ryuk a la fin). Avec
    // @Testcontainers/@Container statique il serait redemarre (autre port) a chaque classe de test alors que
    // le contexte Spring est mis en cache : toutes les classes suivantes echoueraient a se connecter.
    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected EditionRepository editionRepository;
    @Autowired protected PhaseRepository phaseRepository;
    @Autowired protected SoireeEventRepository soireeEventRepository;
    @Autowired protected CategorieTicketRepository categorieTicketRepository;
    @Autowired protected ReservationRepository reservationRepository;
    @Autowired protected TicketRepository ticketRepository;
    @Autowired protected QRCodeTicketRepository qrCodeTicketRepository;
    @Autowired protected UtilisateurRepository utilisateurRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected CandidatRepository candidatRepository;
    @Autowired protected PouleRepository pouleRepository;
    @Autowired protected AffectationPouleRepository affectationPouleRepository;
    @Autowired protected DroitVoteSurPlaceRepository droitVoteSurPlaceRepository;
    @Autowired protected VoteRepository voteRepository;

    protected static String randomPhone() {
        return String.format("+22670%06d", SEQ.incrementAndGet());
    }

    protected static String randomEmail() {
        return "test-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@nks.local";
    }

    protected Edition creerEdition() {
        Edition edition = Edition.builder()
                .nom("Edition Test " + SEQ.incrementAndGet())
                .annee((short) 2026)
                .build();
        return editionRepository.save(edition);
    }

    protected Phase creerPhase(Edition edition) {
        Phase phase = Phase.builder()
                .edition(edition)
                .nom(NomPhase.ELIMINATOIRES)
                .typePhase("ELIMINATOIRES")
                .ordre((short) 1)
                .poidsVotesEnLigne((short) 25)
                .poidsPublicSurPlace((short) 25)
                .poidsJury((short) 50)
                .build();
        return phaseRepository.save(phase);
    }

    protected SoireeEvent creerSoiree(Edition edition, Phase phase, boolean voteSurPlaceActif) {
        SoireeEvent soiree = SoireeEvent.builder()
                .edition(edition)
                .phase(phase)
                .nom("Soiree test " + SEQ.incrementAndGet())
                .dateHeure(Instant.now())
                .capaciteMax(200)
                .voteSurPlaceActif(voteSurPlaceActif)
                .build();
        return soireeEventRepository.save(soiree);
    }

    protected CategorieTicket creerCategorieTicket(SoireeEvent soiree) {
        CategorieTicket categorie = CategorieTicket.builder()
                .soiree(soiree)
                .nom(NomCategorieTicket.STANDARD)
                .prix(BigDecimal.valueOf(1000))
                .nbPlacesDisponibles(100)
                .build();
        return categorieTicketRepository.save(categorie);
    }

    protected Reservation creerReservation(SoireeEvent soiree, String telephone, int nbPlaces) {
        Reservation reservation = Reservation.builder()
                .soiree(soiree)
                .telephoneReservant(telephone)
                .nomReservant("Client Test")
                .nbPlaces(nbPlaces)
                .gratuit(true)
                .build();
        return reservationRepository.save(reservation);
    }

    protected Ticket creerTicket(Reservation reservation, SoireeEvent soiree, CategorieTicket categorie,
                                  String telephoneSpectateur) {
        Ticket ticket = Ticket.builder()
                .reservation(reservation)
                .soiree(soiree)
                .categorie(categorie)
                .nomSpectateur("Spectateur Test")
                .telephoneSpectateur(telephoneSpectateur)
                .statut(StatutTicket.EMIS)
                .build();
        return ticketRepository.save(ticket);
    }

    protected QRCodeTicket creerQrCode(Ticket ticket) {
        QRCodeTicket qr = QRCodeTicket.builder()
                .ticket(ticket)
                .build();
        return qrCodeTicketRepository.save(qr);
    }

    protected QRCodeTicket creerBilletComplet(SoireeEvent soiree, CategorieTicket categorie,
                                               Reservation reservation, String telephoneSpectateur) {
        Ticket ticket = creerTicket(reservation, soiree, categorie, telephoneSpectateur);
        return creerQrCode(ticket);
    }

    protected Utilisateur creerUtilisateurAvecRole(RoleName roleName) {
        Role role = roleRepository.findByNom(roleName)
                .orElseThrow(() -> new IllegalStateException("Role " + roleName + " introuvable"));
        Utilisateur utilisateur = Utilisateur.builder()
                .email(randomEmail())
                .telephone(randomPhone())
                .motDePasseHash("hash-test")
                .prenom("Test")
                .nom(roleName.name())
                .roles(new java.util.HashSet<>(java.util.Set.of(role)))
                .build();
        return utilisateurRepository.save(utilisateur);
    }

    protected Candidat creerCandidat(Edition edition) {
        Utilisateur utilisateur = Utilisateur.builder()
                .email(randomEmail())
                .telephone(randomPhone())
                .motDePasseHash("hash-test")
                .prenom("Candidat")
                .nom("Test")
                .build();
        utilisateur = utilisateurRepository.save(utilisateur);

        Candidat candidat = Candidat.builder()
                .utilisateur(utilisateur)
                .edition(edition)
                .codeCandidat("C" + SEQ.incrementAndGet())
                .dateNaissance(LocalDate.of(1995, 1, 1))
                .ageALInscription((short) 30)
                .statutProfil(StatutProfilCandidat.ACTIF)
                .build();
        return candidatRepository.save(candidat);
    }

    protected AffectationPoule affecterCandidatALaSoiree(Candidat candidat, Phase phase, SoireeEvent soiree) {
        Poule poule = Poule.builder()
                .phase(phase)
                .nom("Poule test " + SEQ.incrementAndGet())
                .soiree(soiree)
                .build();
        poule = pouleRepository.save(poule);

        AffectationPoule affectation = AffectationPoule.builder()
                .candidat(candidat)
                .poule(poule)
                .build();
        return affectationPouleRepository.save(affectation);
    }
}
