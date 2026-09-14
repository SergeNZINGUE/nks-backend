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

    /**
     * Nombre max de tentatives de connexion (login) ET, par réutilisation délibérée
     * (correctif audit IDOR billetterie), nombre max de tentatives de saisie d'un même
     * code OTP avant invalidation de l'entrée — cf. OtpTicketVerificationService.
     */
    static final int MAX_TENTATIVES = 5;
    private static final Duration FENETRE = Duration.ofMinutes(15);

    // Correctif audit IDOR billetterie : demande de code OTP (§ /reservations/mes-tickets/otp/demander).
    private static final int MAX_DEMANDES_OTP_PAR_TELEPHONE = 3;
    private static final int MAX_DEMANDES_OTP_PAR_IP = 10;
    private static final Duration FENETRE_OTP = Duration.ofMinutes(10);

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

    /**
     * Deux limites actives simultanément (par numéro normalisé ET par IP) : la limite par
     * numéro empêche le spam d'un numéro ciblé, la limite par IP empêche un tiers d'épuiser
     * le quota fournisseur (100 msg/mois, cf. NKS_WHATSAPP_TEMPLATES.md) en variant les numéros.
     */
    public void verifierDemandeOtp(String ip, String telephoneNormalise) {
        Bucket parTelephone = buckets.computeIfAbsent("otp|tel|" + telephoneNormalise, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.simple(MAX_DEMANDES_OTP_PAR_TELEPHONE, FENETRE_OTP))
                        .build());
        if (!parTelephone.tryConsume(1)) {
            throw new TropDeTentativesException("Trop de demandes de code pour ce numéro. Réessayez dans 10 minutes.");
        }

        Bucket parIp = buckets.computeIfAbsent("otp|ip|" + ip, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.simple(MAX_DEMANDES_OTP_PAR_IP, FENETRE_OTP))
                        .build());
        if (!parIp.tryConsume(1)) {
            throw new TropDeTentativesException("Trop de demandes de code depuis cette connexion. Réessayez dans 10 minutes.");
        }
    }

    /**
     * Purge les buckets pleins (fenêtre expirée) toutes les 30 minutes. Compare aux capacités
     * respectives (login, OTP/téléphone, OTP/IP diffèrent) plutôt qu'à une constante unique,
     * pour que les buckets OTP soient bien éligibles à la purge eux aussi.
     */
    @Scheduled(fixedRate = 1_800_000)
    void purgerBuckets() {
        int avant = buckets.size();
        buckets.entrySet().removeIf(e -> {
            long disponibles = e.getValue().getAvailableTokens();
            String cle = e.getKey();
            long capacite = cle.startsWith("otp|tel|") ? MAX_DEMANDES_OTP_PAR_TELEPHONE
                    : cle.startsWith("otp|ip|") ? MAX_DEMANDES_OTP_PAR_IP
                    : MAX_TENTATIVES;
            return disponibles == capacite;
        });
        log.debug("Purge rate-limit : {} → {} entrées", avant, buckets.size());
    }
}
