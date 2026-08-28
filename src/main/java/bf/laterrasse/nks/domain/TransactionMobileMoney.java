package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.OperateurMobileMoney;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions_mobile_money")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionMobileMoney {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paiement_id", nullable = false)
    private Paiement paiement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperateurMobileMoney operateur;

    @Column(name = "reference_operateur", nullable = false, length = 150)
    private String referenceOperateur;

    @Column(nullable = false)
    private BigDecimal montant;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String devise = "XOF";

    @Column(name = "telephone_payeur", length = 20)
    private String telephonePayeur;

    @Column(name = "statut_operateur", length = 50)
    private String statutOperateur;

    @Column(name = "token_creation", length = 255)
    private String tokenCreation;

    @Column(name = "code_reponse", length = 20)
    private String codeReponse;

    @Column(name = "motif_rejet", columnDefinition = "text")
    private String motifRejet;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "webhook_payload", columnDefinition = "jsonb")
    private String webhookPayload;

    /** Déprécié — LigdiCash ne signe pas ses webhooks. Conservé pour compatibilité schéma. */
    @Column(name = "signature_webhook", length = 255)
    private String signatureWebhook;

    @Column(name = "date_webhook")
    private Instant dateWebhook;
}
