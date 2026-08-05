package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Journal append-only des actions critiques. Jamais modifié ni supprimé — voir
 * V1__init_schema.sql (REVOKE DELETE, UPDATE) et AuditAspect pour l'écriture automatique.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    private Utilisateur utilisateur;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entite_concernee", nullable = false, length = 100)
    private String entiteConcernee;

    @Column(name = "entite_id")
    private UUID entiteId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "donnees_avant", columnDefinition = "jsonb")
    private String donneesAvant;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "donnees_apres", columnDefinition = "jsonb")
    private String donneesApres;

    @Column(name = "ip_source", length = 45)
    private String ipSource;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();
}
