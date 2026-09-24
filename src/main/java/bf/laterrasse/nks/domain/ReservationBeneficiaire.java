package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Personne (numéro de téléphone) à qui sera remis le i-ème billet d'une réservation. Persisté
 * dès l'initiation de la réservation (les billets ne naissent qu'à la confirmation du
 * paiement). {@code actif=false} dès que la place est libérée — l'index unique partiel
 * {@code ux_reservation_beneficiaires_soiree_tel_actif} garantit alors en base qu'un
 * numéro n'a qu'un bénéficiaire actif par soirée. Références en UUID simples (FK en base) :
 * cette table n'est jamais parcourue en graphe d'entités.
 */
@Entity
@Table(name = "reservation_beneficiaires")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationBeneficiaire {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "reservation_id", nullable = false, columnDefinition = "uuid")
    private UUID reservationId;

    @Column(name = "soiree_id", nullable = false, columnDefinition = "uuid")
    private UUID soireeId;

    @Column(name = "position", nullable = false)
    private short position;

    @Column(length = 150)
    private String nom;

    /** E.164 (SmsGateway.normaliserTelephone). */
    @Column(nullable = false, length = 20)
    private String telephone;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(name = "date_creation", nullable = false)
    @Builder.Default
    private Instant dateCreation = Instant.now();
}
