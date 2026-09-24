package bf.laterrasse.nks.job;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.enums.Enums.NomCategorieTicket;
import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import bf.laterrasse.nks.repository.CategorieTicketRepository;
import bf.laterrasse.nks.repository.ReservationBeneficiaireRepository;
import bf.laterrasse.nks.repository.ReservationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** L'expiration d'une pre-reservation libere ses places ET les numeros de ses beneficiaires. */
@ExtendWith(MockitoExtension.class)
class ReservationExpiryJobTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private CategorieTicketRepository categorieTicketRepository;
    @Mock private ReservationBeneficiaireRepository beneficiaireRepository;

    @Test
    @DisplayName("Pre-reservation PENDING expiree : EXPIREE, beneficiaires desactives, places rendues")
    void expiration_liberreNumerosEtPlaces() {
        UUID soireeId = UUID.randomUUID();
        SoireeEvent soiree = SoireeEvent.builder().id(soireeId).build();
        CategorieTicket categorie = CategorieTicket.builder().id(UUID.randomUUID()).soiree(soiree)
                .nom(NomCategorieTicket.STANDARD).nbPlacesDisponibles(100).nbPlacesReservees(5).build();
        Reservation expiree = Reservation.builder().id(UUID.randomUUID()).soiree(soiree).categorie(categorie)
                .nbPlaces(2).statut(StatutReservation.PENDING).build();
        when(reservationRepository.findByStatutAndDateExpirationBefore(any(), any(Instant.class)))
                .thenReturn(List.of(expiree));
        when(categorieTicketRepository.findByIdForUpdate(categorie.getId())).thenReturn(java.util.Optional.of(categorie));

        new ReservationExpiryJob(reservationRepository, categorieTicketRepository, beneficiaireRepository)
                .expirerPreReservations();

        assertThat(expiree.getStatut()).isEqualTo(StatutReservation.EXPIREE);
        verify(beneficiaireRepository).desactiverParReservation(expiree.getId());
        assertThat(categorie.getNbPlacesReservees()).isEqualTo(3);
    }

    @Test
    @DisplayName("Reservation expiree sans categorie stockee (legacy) : repli sur la 1ere categorie active de la soiree, verrouillee")
    void expiration_sansCategorieStockee_repliSurPremiereCategorieDeLaSoiree() {
        UUID soireeId = UUID.randomUUID();
        SoireeEvent soiree = SoireeEvent.builder().id(soireeId).build();
        CategorieTicket categorie = CategorieTicket.builder().id(UUID.randomUUID()).soiree(soiree)
                .nom(NomCategorieTicket.STANDARD).nbPlacesDisponibles(100).nbPlacesReservees(5).build();
        Reservation expiree = Reservation.builder().id(UUID.randomUUID()).soiree(soiree).nbPlaces(2)
                .statut(StatutReservation.PENDING).build();
        when(reservationRepository.findByStatutAndDateExpirationBefore(any(), any(Instant.class)))
                .thenReturn(List.of(expiree));
        when(categorieTicketRepository.findBySoireeId(soireeId)).thenReturn(List.of(categorie));
        when(categorieTicketRepository.findByIdForUpdate(categorie.getId())).thenReturn(java.util.Optional.of(categorie));

        new ReservationExpiryJob(reservationRepository, categorieTicketRepository, beneficiaireRepository)
                .expirerPreReservations();

        assertThat(categorie.getNbPlacesReservees()).isEqualTo(3);
    }

    @Test
    @DisplayName("Aucune pre-reservation expiree : aucune liberation")
    void aucuneExpiration_neFaitRien() {
        when(reservationRepository.findByStatutAndDateExpirationBefore(any(), any(Instant.class)))
                .thenReturn(List.of());

        new ReservationExpiryJob(reservationRepository, categorieTicketRepository, beneficiaireRepository)
                .expirerPreReservations();

        verify(beneficiaireRepository, never()).desactiverParReservation(any());
    }
}
