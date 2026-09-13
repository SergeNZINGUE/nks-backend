package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutDroitVote;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Droit de vote sur place adossé à un billet (Ticket) déjà scanné à l'entrée (anti-fraude
 * §14.6 : le ticket ne peut être marqué UTILISE qu'une seule fois, verrou pessimiste sur le
 * QR code). Un caissier valide ce droit au moment d'une consommation réelle au bar — un seul
 * droit par billet, jamais deux (contrainte UNIQUE ticket_id) — puis le client l'exprime une
 * seule fois via /vote-sur-place. Ce mécanisme garantit 1 personne physique entrée = 1 vote
 * sur place maximum pour cette soirée, sans dépendre d'un numéro de téléphone (facilement
 * dupliqué) ni d'un système de bracelet séparé.
 */
@Entity
@Table(name = "droits_vote_sur_place")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DroitVoteSurPlace {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id", nullable = false)
    private SoireeEvent soiree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caissier_id", nullable = false)
    private Utilisateur caissier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutDroitVote statut = StatutDroitVote.DISPONIBLE;

    @Column(name = "date_emission", nullable = false)
    @Builder.Default
    private Instant dateEmission = Instant.now();

    /** true dès que le lien de vote a été transmis par WhatsApp (best-effort, cf. VoteSurPlaceService). */
    @Column(name = "lien_whatsapp_envoye", nullable = false)
    @Builder.Default
    private boolean lienWhatsappEnvoye = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id")
    private Candidat candidat;

    @Column(name = "date_vote")
    private Instant dateVote;

    // Champs d'audit uniquement — jamais utilisés pour bloquer un vote (cf. Javadoc classe) :
    // permettent de recouper a posteriori en cas de contestation, pas de vérification en temps réel.

    @Column(name = "telephone_votant", length = 20)
    private String telephoneVotant;

    @Column(name = "position_latitude", precision = 10, scale = 6)
    private java.math.BigDecimal positionLatitude;

    @Column(name = "position_longitude", precision = 10, scale = 6)
    private java.math.BigDecimal positionLongitude;

    @Column(name = "position_precision_m", precision = 10, scale = 2)
    private java.math.BigDecimal positionPrecisionM;
}
