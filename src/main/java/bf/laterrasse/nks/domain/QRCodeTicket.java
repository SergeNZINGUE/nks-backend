package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "qrcodes_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QRCodeTicket {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    @Column(name = "code_uuid", nullable = false, unique = true)
    @Builder.Default
    private UUID codeUuid = UUID.randomUUID();

    @Column(name = "url_ticket", length = 500)
    private String urlTicket;

    @Column(name = "date_generation", nullable = false)
    @Builder.Default
    private Instant dateGeneration = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private boolean valide = true;
}
