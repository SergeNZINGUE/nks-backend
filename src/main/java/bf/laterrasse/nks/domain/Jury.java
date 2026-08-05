package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutJury;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "jurys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Jury {

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

    @Column(nullable = false, length = 100)
    private String prenom;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(length = 150)
    private String specialite;

    @Column(name = "bio_publique", columnDefinition = "text")
    private String bioPublique;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutJury statut = StatutJury.ACTIF;

    @ManyToMany
    @JoinTable(
            name = "jurys_soirees",
            joinColumns = @JoinColumn(name = "jury_id"),
            inverseJoinColumns = @JoinColumn(name = "soiree_id")
    )
    @Builder.Default
    private Set<SoireeEvent> soirees = new HashSet<>();
}
