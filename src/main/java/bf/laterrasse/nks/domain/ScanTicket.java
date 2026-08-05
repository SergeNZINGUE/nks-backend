package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.ResultatScan;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scans_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScanTicket {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "qrcode_id", nullable = false)
    private QRCodeTicket qrcode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id", nullable = false)
    private SoireeEvent soiree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Utilisateur agent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultatScan resultat;

    @Column(name = "timestamp_scan", nullable = false)
    @Builder.Default
    private Instant timestampScan = Instant.now();

    @Column(name = "ip_agent", length = 45)
    private String ipAgent;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;
}
