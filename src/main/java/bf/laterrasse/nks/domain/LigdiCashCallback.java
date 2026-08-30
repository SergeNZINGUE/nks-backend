package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Verrou de déduplication des callbacks LigdiCash.
 * LigdiCash envoie 2 POST par événement (form-encoded + JSON) — l'INSERT atomique
 * sur la contrainte UNIQUE (token) garantit qu'un seul est traité, l'autre est ignoré.
 */
@Entity
@Table(name = "ligdicash_callbacks")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LigdiCashCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String token;

    @Column(name = "recu_le", nullable = false)
    private Instant recuLe;

    public LigdiCashCallback(String token) {
        this.token = token;
        this.recuLe = Instant.now();
    }
}
