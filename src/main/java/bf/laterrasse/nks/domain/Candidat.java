package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "candidats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidat {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id", nullable = false, unique = true)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(name = "code_candidat", nullable = false, length = 10)
    private String codeCandidat;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Column(name = "age_a_l_inscription", nullable = false)
    private Short ageALInscription;

    @Column(columnDefinition = "text")
    private String biographie;

    @Column(name = "chanson_preselection", length = 255)
    private String chansonPreselection;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_profil", nullable = false, length = 20)
    @Builder.Default
    private StatutProfilCandidat statutProfil = StatutProfilCandidat.EN_ATTENTE;

    @Column(name = "date_activation_profil")
    private Instant dateActivationProfil;

    /** Publication officielle du candidat sur chaque réseau — alimente le polling des votes sociaux (V2 migration). */
    @Column(name = "post_id_facebook", length = 100)
    private String postIdFacebook;

    @Column(name = "post_id_tiktok", length = 100)
    private String postIdTiktok;
}
