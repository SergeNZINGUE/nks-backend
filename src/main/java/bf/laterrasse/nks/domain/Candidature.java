package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutCandidature;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "candidatures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidature {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private StatutCandidature statut = StatutCandidature.EN_ATTENTE;

    @Column(length = 1500)
    private String motivation;

    @Column(name = "capture_fb_tiktok_url", length = 500)
    private String captureFbTiktokUrl;

    @Column(name = "date_soumission", nullable = false)
    @Builder.Default
    private Instant dateSoumission = Instant.now();

    @Column(name = "date_traitement_admin")
    private Instant dateTraitementAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Utilisateur admin;

    @Column(name = "motif_rejet", columnDefinition = "text")
    private String motifRejet;

    @Column(name = "date_modification")
    private Instant dateModification;
}
