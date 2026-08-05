package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutMedia;
import bf.laterrasse.nks.domain.enums.Enums.TypeMedia;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeMedia type;

    @Column(name = "url_stockage", length = 500)
    private String urlStockage;

    @Column(name = "nom_fichier_original", length = 255)
    private String nomFichierOriginal;

    @Column(name = "taille_octets", nullable = false)
    private Long tailleOctets;

    @Column(nullable = false, length = 10)
    private String format;

    @Column(name = "date_upload", nullable = false)
    @Builder.Default
    private Instant dateUpload = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutMedia statut = StatutMedia.EN_ATTENTE;
}
