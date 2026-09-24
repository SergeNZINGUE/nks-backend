package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Appareil ayant déjà voté pour un billet lors d'une soirée (blocage "dur"). Seul le
 * SHA-256 de l'uuid du jeton d'appareil est stocké, jamais le jeton. L'insertion se fait via
 * {@code AppareilVoteSurPlaceRepository#insererSiAbsent} (ON CONFLICT DO NOTHING) ; l'entité
 * ne sert qu'à la lecture (JPQL).
 */
@Entity
@Table(name = "appareils_vote_sur_place")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppareilVoteSurPlace {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "soiree_id", nullable = false, columnDefinition = "uuid")
    private UUID soireeId;

    @Column(name = "appareil_hash", nullable = false, length = 64)
    private String appareilHash;

    @Column(name = "ticket_id", nullable = false, columnDefinition = "uuid")
    private UUID ticketId;

    @Column(name = "premier_vote", nullable = false)
    private Instant premierVote;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(length = 128)
    private String empreinte;
}
