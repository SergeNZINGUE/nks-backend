package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "votes_payants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VotePayant {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vote_id", nullable = false)
    private Vote vote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paiement_id", nullable = false)
    private Paiement paiement;

    @Column(name = "nombre_votes_achetes", nullable = false)
    private Integer nombreVotesAchetes;

    @Column(name = "montant_total", nullable = false)
    private BigDecimal montantTotal;

    @Column(name = "telephone_votant", nullable = false, length = 20)
    private String telephoneVotant;
}
