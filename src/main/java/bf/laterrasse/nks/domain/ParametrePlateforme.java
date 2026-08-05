package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.TypeValeurParametre;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Configuration globale modifiable par le super admin. Sert notamment à stocker les
 * valeurs décidées avec le client sans les figer en dur dans le code : tarif d'inscription,
 * seuil anti-fraude votes, politique de remboursement, tailles max de fichiers.
 */
@Entity
@Table(name = "parametres_plateforme")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParametrePlateforme {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String cle;

    @Column(nullable = false, columnDefinition = "text")
    private String valeur;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_valeur", nullable = false, length = 10)
    private TypeValeurParametre typeValeur;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "modifiable_par_admin", nullable = false)
    @Builder.Default
    private boolean modifiableParAdmin = true;

    @Column(name = "date_modification")
    private Instant dateModification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifie_par")
    private Utilisateur modifiePar;
}
