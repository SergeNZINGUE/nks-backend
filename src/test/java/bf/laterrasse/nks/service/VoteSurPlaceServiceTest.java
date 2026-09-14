package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.DroitVoteSurPlace;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.enums.Enums.ResultatScan;
import bf.laterrasse.nks.domain.enums.Enums.StatutDroitVote;
import bf.laterrasse.nks.dto.scan.ScanResponse;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.gateway.sms.WhatsappGateway;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.DroitVoteSurPlaceRepository;
import bf.laterrasse.nks.repository.DuoRepository;
import bf.laterrasse.nks.repository.QRCodeTicketRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests (Mockito, no Spring context, no real database) for the anti-fraud invariants
 * carried by VoteSurPlaceService:
 *  - a droit already UTILISE can never produce a second Vote;
 *  - a ticket that already has a DroitVoteSurPlace cannot get a second one via
 *    validerConsommation (application-level check, complementary to the DB UNIQUE
 *    constraint exercised separately in the integration tests).
 */
@ExtendWith(MockitoExtension.class)
class VoteSurPlaceServiceTest {

    @Mock private QRCodeTicketRepository qrCodeTicketRepository;
    @Mock private DroitVoteSurPlaceRepository droitVoteSurPlaceRepository;
    @Mock private SoireeEventRepository soireeEventRepository;
    @Mock private CandidatRepository candidatRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private AffectationPouleRepository affectationPouleRepository;
    @Mock private DuoRepository duoRepository;
    @Mock private WhatsappGateway whatsappGateway;
    @Mock private ScanService scanService;

    private VoteSurPlaceService service;

    private final UUID soireeId = UUID.randomUUID();
    private final UUID qrUuid = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VoteSurPlaceService(qrCodeTicketRepository, droitVoteSurPlaceRepository,
                soireeEventRepository, candidatRepository, voteRepository, affectationPouleRepository,
                duoRepository, whatsappGateway, scanService);
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:4200");
    }

    private SoireeEvent soiree() {
        return SoireeEvent.builder().id(soireeId).voteSurPlaceActif(true)
                .phase(Phase.builder().id(UUID.randomUUID()).build())
                .build();
    }

    private Ticket ticketPourSoiree(SoireeEvent soiree) {
        return Ticket.builder().id(ticketId).soiree(soiree)
                .nomSpectateur("Client Test").telephoneSpectateur("+22670000001").build();
    }

    private void stubResolutionTicket(SoireeEvent soiree, Ticket ticket) {
        when(soireeEventRepository.findById(soireeId)).thenReturn(Optional.of(soiree));
        QRCodeTicket qr = QRCodeTicket.builder().id(UUID.randomUUID()).ticket(ticket).codeUuid(qrUuid).build();
        when(qrCodeTicketRepository.findByCodeUuid(qrUuid)).thenReturn(Optional.of(qr));
    }

    @Test
    @DisplayName("voter() on an already UTILISE droit throws ConflitEtatException and creates no second Vote")
    void voter_droitDejaUtilise_lanceConflitEtatException_sansCreerDeuxiemeVote() {
        SoireeEvent soiree = soiree();
        Ticket ticket = ticketPourSoiree(soiree);
        stubResolutionTicket(soiree, ticket);

        DroitVoteSurPlace droitDejaUtilise = DroitVoteSurPlace.builder()
                .id(UUID.randomUUID()).ticket(ticket).soiree(soiree)
                .statut(StatutDroitVote.UTILISE)
                .candidat(Candidat.builder().id(UUID.randomUUID()).build())
                .build();
        when(droitVoteSurPlaceRepository.findByTicketIdForUpdate(ticketId)).thenReturn(Optional.of(droitDejaUtilise));

        assertThatThrownBy(() -> service.voter(qrUuid, soireeId, UUID.randomUUID(), null, null, null, null))
                .isInstanceOf(ConflitEtatException.class)
                .hasMessageContaining("Ce billet a");

        verify(voteRepository, never()).save(any());
        verify(droitVoteSurPlaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("voter() on a DISPONIBLE droit creates exactly one Vote and flips the droit to UTILISE")
    void voter_droitDisponible_creeUnVoteEtPasseAUtilise() {
        SoireeEvent soiree = soiree();
        Ticket ticket = ticketPourSoiree(soiree);
        stubResolutionTicket(soiree, ticket);

        DroitVoteSurPlace droitDisponible = DroitVoteSurPlace.builder()
                .id(UUID.randomUUID()).ticket(ticket).soiree(soiree)
                .statut(StatutDroitVote.DISPONIBLE)
                .build();
        when(droitVoteSurPlaceRepository.findByTicketIdForUpdate(ticketId)).thenReturn(Optional.of(droitDisponible));

        UUID candidatId = UUID.randomUUID();
        Candidat candidat = Candidat.builder().id(candidatId).build();
        when(candidatRepository.findById(candidatId)).thenReturn(Optional.of(candidat));

        AffectationPoule affectation = AffectationPoule.builder().id(UUID.randomUUID()).candidat(candidat).build();
        when(affectationPouleRepository.findByPouleSoireeId(soireeId)).thenReturn(List.of(affectation));
        when(duoRepository.findBySoireeId(soireeId)).thenReturn(List.of());

        when(droitVoteSurPlaceRepository.save(any(DroitVoteSurPlace.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.voter(qrUuid, soireeId, candidatId, "+22670009999", null, null, null);

        verify(voteRepository, org.mockito.Mockito.times(1)).save(any());

        ArgumentCaptor<DroitVoteSurPlace> captor = ArgumentCaptor.forClass(DroitVoteSurPlace.class);
        verify(droitVoteSurPlaceRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutDroitVote.UTILISE);
        assertThat(captor.getValue().getCandidat()).isEqualTo(candidat);
        assertThat(captor.getValue().getDateVote()).isNotNull();
    }

    @Test
    @DisplayName("validerConsommation() refuses a second droit for a ticket that already has one")
    void validerConsommation_billetADejaUnDroit_lanceConflitEtatException() {
        SoireeEvent soiree = soiree();
        Ticket ticket = ticketPourSoiree(soiree);

        when(scanService.scanner(any(), any(), any(), any(), any()))
                .thenReturn(new ScanResponse(ResultatScan.DEJA_UTILISE.name(), ticket.getNomSpectateur(), 1, null));
        stubResolutionTicket(soiree, ticket);
        when(droitVoteSurPlaceRepository.existsByTicketId(ticketId)).thenReturn(true);

        assertThatThrownBy(() -> service.validerConsommation(qrUuid, soireeId, null, "127.0.0.1", "device"))
                .isInstanceOf(ConflitEtatException.class)
                .hasMessageContaining("Ce billet a");

        verify(droitVoteSurPlaceRepository, never()).save(any());
    }
}
