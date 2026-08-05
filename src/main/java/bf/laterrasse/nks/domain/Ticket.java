package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id", nullable = false)
    private SoireeEvent soiree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieTicket categorie;

    @Column(name = "nom_spectateur", nullable = false, length = 150)
    private String nomSpectateur;

    @Column(name = "telephone_spectateur", nullable = false, length = 20)
    private String telephoneSpectateur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutTicket statut = StatutTicket.EMIS;

    @Column(name = "date_emission", nullable = false)
    @Builder.Default
    private Instant dateEmission = Instant.now();

    @Column(name = "date_annulation")
    private Instant dateAnnulation;
}
