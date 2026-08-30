package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutPaiement;
import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paiements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paiement {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_paiement", nullable = false, length = 20)
    private TypePaiement typePaiement;

    @Column(nullable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutPaiement statut = StatutPaiement.PENDING;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    @Builder.Default
    private UUID idempotencyKey = UUID.randomUUID();

    @Column(name = "date_creation", nullable = false)
    @Builder.Default
    private Instant dateCreation = Instant.now();

    @Column(name = "date_finalisation")
    private Instant dateFinalisation;

    @Column(name = "reference_externe", length = 500)
    private String referenceExterne;

    @Column(nullable = false)
    @Builder.Default
    private boolean manuel = false;

    @Column(name = "nb_tentatives_polling", nullable = false)
    @Builder.Default
    private int nbTentativesPolling = 0;

    @Column(name = "derniere_tentative_polling")
    private Instant derniereTentativePolling;

    @Column(name = "date_expiration")
    private Instant dateExpiration;
}
