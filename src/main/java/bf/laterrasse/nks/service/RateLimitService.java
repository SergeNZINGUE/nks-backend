package bf.laterrasse.nks.service;

import bf.laterrasse.nks.exception.TropDeTentativesException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection brute-force sur /auth/login (§14.8, RM-37).
 * Compteur par (IP + email) : bloque le credential-stuffing distribué
 * sans permettre à un tiers de verrouiller le compte d'autrui depuis une seule IP.
 */
@Service
@Slf4j
public class RateLimitService {

    private static final int MAX_TENTATIVES = 5;
    private static final Duration FENETRE = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void verifierTentativeLogin(String ip, String email) {
        Bucket bucket = buckets.computeIfAbsent(ip + "|" + email, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.simple(MAX_TENTATIVES, FENETRE))
                        .build());
        if (!bucket.tryConsume(1)) {
            throw new TropDeTentativesException("Trop de tentatives de connexion. Réessayez dans 15 minutes.");
        }
    }

    /** Purge les buckets pleins (fenêtre expirée) toutes les 30 minutes. */
    @Scheduled(fixedRate = 1_800_000)
    void purgerBuckets() {
        int avant = buckets.size();
        buckets.entrySet().removeIf(e -> e.getValue().getAvailableTokens() == MAX_TENTATIVES);
        log.debug("Purge rate-limit : {} → {} entrées", avant, buckets.size());
    }
}
