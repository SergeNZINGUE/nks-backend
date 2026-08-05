package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutEdition;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "editions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Edition {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(nullable = false)
    private Short annee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutEdition statut = StatutEdition.EN_PREPARATION;

    @Column(name = "date_debut_inscriptions")
    private LocalDate dateDebutInscriptions;

    @Column(name = "date_fin_inscriptions")
    private LocalDate dateFinInscriptions;

    @Column(name = "date_debut_competition")
    private LocalDate dateDebutCompetition;

    @Column(name = "date_fin_competition")
    private LocalDate dateFinCompetition;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "date_creation", nullable = false)
    @Builder.Default
    private Instant dateCreation = Instant.now();

    @Column(name = "date_modification")
    private Instant dateModification;

    @PrePersist
    private void avantInsert() {
        if (statut == null) statut = StatutEdition.EN_PREPARATION;
        if (dateCreation == null) dateCreation = Instant.now();
    }
}
