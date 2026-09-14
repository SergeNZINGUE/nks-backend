package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Correctif IDOR billetterie (audit sécurité) : une entrée par code OTP envoyé pour
 * prouver la possession d'un numéro de téléphone avant d'accéder/annuler ses réservations.
 * Le code en clair n'est jamais stocké (voir {@code code_hash}) ni journalisé.
 */
@Entity
@Table(name = "otp_ticket_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpTicketVerification {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 20)
    private String telephone;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expire_at", nullable = false)
    private Instant expireAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean consomme = false;

    @Column(name = "nb_tentatives", nullable = false)
    @Builder.Default
    private short nbTentatives = 0;

    @Column(name = "date_creation", nullable = false)
    @Builder.Default
    private Instant dateCreation = Instant.now();
}
