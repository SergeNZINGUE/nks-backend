package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Remboursement billetterie décidé avec le client : traitement manuel au cas par cas
 * (pas d'automatisation). Voir BilletterieService.annulerReservation().
 */
@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id", nullable = false)
    private SoireeEvent soiree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paiement_id")
    private Paiement paiement;

    @Column(name = "telephone_reservant", nullable = false, length = 20)
    private String telephoneReservant;

    @Column(name = "nom_reservant", nullable = false, length = 150)
    private String nomReservant;

    @Column(name = "email_reservant", length = 255)
    private String emailReservant;

    @Column(name = "nb_places", nullable = false)
    private Integer nbPlaces;

    @Column(name = "montant_total", nullable = false)
    @Builder.Default
    private BigDecimal montantTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutReservation statut = StatutReservation.PENDING;

    @Column(name = "date_reservation", nullable = false)
    @Builder.Default
    private Instant dateReservation = Instant.now();

    @Column(name = "date_expiration")
    private Instant dateExpiration;

    @Column(nullable = false)
    @Builder.Default
    private boolean gratuit = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id_emission")
    private Utilisateur adminEmission;
}
