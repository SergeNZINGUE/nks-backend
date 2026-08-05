package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutVideo;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Taille max = 100 Mo (décision client tranchant l'incohérence RM-05 du cahier des charges).
 */
@Entity
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Video {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id")
    private Phase phase;

    @Column(name = "url_stockage_originale", length = 500)
    private String urlStockageOriginale;

    @Column(name = "url_streaming", length = 500)
    private String urlStreaming;

    @Column(name = "url_thumbnail", length = 500)
    private String urlThumbnail;

    @Column(name = "duree_secondes")
    private Integer dureeSecondes;

    @Column(name = "taille_octets", nullable = false)
    private Long tailleOctets;

    @Column(name = "titre_chanson", length = 255)
    private String titreChanson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutVideo statut = StatutVideo.EN_COURS_UPLOAD;

    @Column(name = "date_upload", nullable = false)
    @Builder.Default
    private Instant dateUpload = Instant.now();
}
