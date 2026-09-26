package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutMedia;
import bf.laterrasse.nks.domain.enums.Enums.TypeMoment;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * "Moments de l'événement" — photos/vidéos souvenirs de la soirée, distinctes de la
 * photo de profil et de la vidéo de prestation (Media/Video) : accumulation libre
 * (plusieurs par candidat, contrairement à l'upsert-par-type de Media), zéro à plusieurs
 * candidats visibles sur un même média, éventuellement ajoutées directement par un
 * admin/organisateur sans passer par la file de modération candidat.
 *
 * Table dédiée plutôt qu'extension de Media : medias.taille_octets est plafonné en dur
 * à 5 Mo par contrainte CHECK (V1__init_schema.sql) — incompatible avec des vidéos
 * jusqu'à 100 Mo — et medias.candidat_id y est NOT NULL, ce que cette fonctionnalité doit
 * pouvoir violer (ajout admin sans candidat associé). Modifier ces deux contraintes sur
 * une table déjà utilisée en production (photo de profil, capture réseau social) aurait
 * été plus risqué qu'une table neuve et isolée.
 */
@Entity
@Table(name = "moments_evenement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MomentEvenement {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TypeMoment type;

    @Column(name = "url_stockage", length = 500, nullable = false)
    private String urlStockage;

    @Column(name = "nom_fichier_original", length = 255)
    private String nomFichierOriginal;

    @Column(name = "taille_octets", nullable = false)
    private Long tailleOctets;

    @Column(length = 10, nullable = false)
    private String format;

    @Column(length = 140)
    private String legende;

    @Column(name = "motif_rejet", columnDefinition = "text")
    private String motifRejet;

    @Column(name = "en_vedette", nullable = false)
    @Builder.Default
    private boolean enVedette = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutMedia statut = StatutMedia.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id")
    private SoireeEvent soiree;

    /**
     * Candidat ayant lui-même soumis ce moment depuis son panel ("Ma Galerie" →
     * "Souvenirs de l'événement") — null si ajouté directement par un admin/organisateur.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_uploadeur_id")
    private Candidat candidatUploadeur;

    /** Admin/organisateur ayant ajouté ce moment directement — null pour un envoi candidat. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ajoute_par_utilisateur_id")
    private Utilisateur ajoutePar;

    /**
     * Candidats visibles/tagués sur ce média (photo de groupe) — distinct de
     * candidatUploadeur, réservé à l'ajout admin/organisateur : jamais renseigné par un
     * candidat sur son propre envoi (pas de tag d'un rival sans son accord).
     */
    @ManyToMany
    @JoinTable(
            name = "moments_evenement_candidats",
            joinColumns = @JoinColumn(name = "moment_id"),
            inverseJoinColumns = @JoinColumn(name = "candidat_id")
    )
    @Builder.Default
    private Set<Candidat> candidatsTagues = new HashSet<>();

    @Column(name = "date_upload", nullable = false)
    @Builder.Default
    private Instant dateUpload = Instant.now();

    @Column(name = "date_moderation")
    private Instant dateModeration;
}
