package bf.laterrasse.nks.service;

import bf.laterrasse.nks.exception.AppareilDejaUtiliseException;
import bf.laterrasse.nks.exception.AppareilInconnuException;
import bf.laterrasse.nks.repository.AppareilVoteSurPlaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Blocage "dur" du vote sur place par appareil : un même appareil ne peut voter que pour UN
 * seul billet par soirée (les votes bonus du MÊME billet restent autorisés).
 *
 * Jeton = {@code <uuid>.<hmac-sha256-base64url>} signé avec le secret dédié
 * {@code nks.vote.device-secret} (jamais réutilisé ailleurs). Rien n'est persisté à
 * l'émission ; au premier vote, seule la ligne (soirée, SHA-256 de l'uuid, billet) est créée.
 *
 * Limite assumée : l'appareil est identifié par un jeton conservé par le navigateur — un
 * utilisateur qui vide son stockage obtient un nouveau jeton. Les signaux souples (IP +
 * user-agent + empreinte, log WARN) servent de filet de détection complémentaire.
 */
@Service
@Slf4j
public class AppareilVoteService {

    static final String DEFAULT_DEV_SECRET = "dev-only-vote-device-secret-change-me-in-prod";
    static final int SECRET_LONGUEUR_MIN = 32;
    private static final String HMAC_ALGO = "HmacSHA256";
    private static final int TOKEN_MAX_LENGTH = 200;
    private static final Pattern EMPREINTE_VALIDE = Pattern.compile("^[A-Za-z0-9+/=_.\\-]{8,128}$");
    private static final long FENETRE_EMISSIONS_MS = 3_600_000L;
    private static final int SEUIL_ALERTE_EMISSIONS_PAR_IP = 30;
    private static final int TAILLE_MAX_TABLE_IP = 10_000;

    /** Contexte d'appel (jamais fiable côté client : sert à la traçabilité et aux signaux souples). */
    public record ContexteAppareil(String token, String ip, String userAgent, String empreinte) {
    }

    private final AppareilVoteSurPlaceRepository repository;
    private final byte[] secret;

    // Signal souple : nombre d'émissions par IP sur la fenêtre courante (en mémoire, pas de blocage).
    private final ConcurrentHashMap<String, AtomicInteger> emissionsParIp = new ConcurrentHashMap<>();
    private volatile long debutFenetre = System.currentTimeMillis();

    public AppareilVoteService(AppareilVoteSurPlaceRepository repository,
                               @Value("${nks.vote.device-secret:}") String secret,
                               @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.repository = repository;
        boolean secretInacceptable = secret == null || secret.isBlank() || DEFAULT_DEV_SECRET.equals(secret)
                || secret.length() < SECRET_LONGUEUR_MIN;
        if (secretInacceptable && !"dev".equalsIgnoreCase(activeProfile) && !"test".equalsIgnoreCase(activeProfile)) {
            throw new IllegalStateException(
                    "NKS_VOTE_DEVICE_SECRET manquant, égal à la valeur de développement ou de moins de " + SECRET_LONGUEUR_MIN + " caractères — obligatoire pour le profil "
                            + activeProfile + " (secret dédié aux jetons d'appareil de vote, distinct de tous les "
                            + "autres secrets). Générez-en un via openssl rand -hex 32.");
        }
        String effectif = (secret == null || secret.isBlank()) ? DEFAULT_DEV_SECRET : secret;
        this.secret = effectif.getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ émission / vérification

    /** Émet un jeton d'appareil ; aucune donnée persistée. */
    public String emettre(String ip) {
        compterEmission(ip);
        String uuid = UUID.randomUUID().toString();
        return uuid + "." + signer(uuid);
    }

    /** @throws AppareilInconnuException si le jeton est absent, mal formé ou de signature invalide. */
    public UUID verifierJeton(String token) {
        return lireJetonSiValide(token).orElseThrow(AppareilInconnuException::new);
    }

    /** Variante silencieuse (lecture seule) : vide si le jeton est absent ou invalide. */
    public Optional<UUID> lireJetonSiValide(String token) {
        if (token == null || token.isBlank() || token.length() > TOKEN_MAX_LENGTH) {
            return Optional.empty();
        }
        int point = token.indexOf('.');
        if (point <= 0 || point != token.lastIndexOf('.') || point == token.length() - 1) {
            return Optional.empty();
        }
        String uuidTexte = token.substring(0, point);
        String signatureRecue = token.substring(point + 1);
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidTexte);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        // Signature recalculée sur la forme canonique de l'uuid, comparaison à temps constant.
        byte[] attendue = signer(uuid.toString()).getBytes(StandardCharsets.UTF_8);
        byte[] recue = signatureRecue.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(attendue, recue)) {
            return Optional.empty();
        }
        return Optional.of(uuid);
    }

    // ------------------------------------------------------------------ règle "un appareil = un billet"

    /** Lecture seule : cet appareil est-il déjà lié à un AUTRE billet de la soirée ? */
    @Transactional(readOnly = true)
    public boolean estLieAUnAutreBillet(UUID soireeId, UUID ticketId, UUID appareilUuid) {
        return repository.findTicketIdBySoireeAndHash(soireeId, hash(appareilUuid))
                .map(liee -> !liee.equals(ticketId))
                .orElse(false);
    }

    /**
     * À appeler dans la transaction de vote (droit déjà verrouillé). Refuse (409) si l'appareil
     * est lié à un autre billet ; sinon lie l'appareil à ce billet (insertion tolérante à la
     * concurrence : ON CONFLICT DO NOTHING puis re-vérification du gagnant).
     */
    @Transactional
    public void enregistrerOuVerifier(UUID soireeId, UUID ticketId, UUID appareilUuid, ContexteAppareil ctx) {
        String hash = hash(appareilUuid);

        Optional<UUID> existant = repository.findTicketIdBySoireeAndHash(soireeId, hash);
        if (existant.isPresent()) {
            verifierMemeBillet(existant.get(), ticketId, soireeId);
            return; // même billet : vote bonus depuis le même appareil, OK
        }

        String ip = tronquer(ctx.ip(), 45);
        String userAgent = tronquer(ctx.userAgent(), 500);
        String empreinte = empreinteValide(ctx.empreinte());
        int insere = repository.insererSiAbsent(UUID.randomUUID(), soireeId, hash, ticketId, ip, userAgent, empreinte);
        if (insere == 0) {
            // Course perdue face à un vote concurrent du même appareil : on re-lit le gagnant.
            UUID gagnant = repository.findTicketIdBySoireeAndHash(soireeId, hash)
                    .orElseThrow(() -> new IllegalStateException("Ligne appareil introuvable après conflit d'unicité"));
            verifierMemeBillet(gagnant, ticketId, soireeId);
            return;
        }
        signalerSignatureCommune(soireeId, ticketId, ip, userAgent, empreinte);
    }

    private void verifierMemeBillet(UUID ticketLie, UUID ticketCourant, UUID soireeId) {
        if (!ticketLie.equals(ticketCourant)) {
            log.warn("Vote sur place refusé : appareil déjà lié à un autre billet (soirée {}, billet demandé {}, billet lié {})",
                    soireeId, ticketCourant, ticketLie);
            throw new AppareilDejaUtiliseException();
        }
    }

    /** Signal souple : ≥2 billets différents partageant (ip + user-agent + empreinte non nulle) — WARN, jamais de blocage. */
    private void signalerSignatureCommune(UUID soireeId, UUID ticketId, String ip, String userAgent, String empreinte) {
        if (ip == null || userAgent == null || empreinte == null) {
            return;
        }
        List<UUID> autres = repository.findAutresTicketsMemeSignature(soireeId, ip, userAgent, empreinte, ticketId);
        if (!autres.isEmpty()) {
            log.warn("SIGNAL_SOUPLE_VOTE soireeId={} ticketId={} autresBillets={} ip={} empreinte={}... : plusieurs billets "
                            + "votés avec la même signature (ip+user-agent+empreinte) — à recouper, aucun blocage appliqué",
                    soireeId, ticketId, autres, ip, empreinte.substring(0, Math.min(8, empreinte.length())));
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private void compterEmission(String ip) {
        long maintenant = System.currentTimeMillis();
        if (maintenant - debutFenetre > FENETRE_EMISSIONS_MS || emissionsParIp.size() > TAILLE_MAX_TABLE_IP) {
            synchronized (this) {
                if (maintenant - debutFenetre > FENETRE_EMISSIONS_MS || emissionsParIp.size() > TAILLE_MAX_TABLE_IP) {
                    emissionsParIp.clear();
                    debutFenetre = maintenant;
                }
            }
        }
        String cle = ip == null ? "inconnue" : ip;
        int total = emissionsParIp.computeIfAbsent(cle, k -> new AtomicInteger()).incrementAndGet();
        if (total >= SEUIL_ALERTE_EMISSIONS_PAR_IP && total % SEUIL_ALERTE_EMISSIONS_PAR_IP == 0) {
            // Signal souple uniquement : les IP sont partagées dans la salle, pas de rate-limit dur.
            log.warn("SIGNAL_SOUPLE_APPAREIL : {} jetons d'appareil émis depuis l'IP {} sur la fenêtre courante (depuis {})",
                    total, cle, Instant.ofEpochMilli(debutFenetre));
        } else {
            log.info("Jeton d'appareil de vote émis (ip={}, émissions sur la fenêtre={})", cle, total);
        }
    }

    private String signer(String uuidCanonique) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            byte[] signature = mac.doFinal(uuidCanonique.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Signature HMAC impossible", e);
        }
    }

    /** SHA-256 hexadécimal de l'uuid du jeton — jamais le jeton brut. */
    static String hash(UUID appareilUuid) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(appareilUuid.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tronquer(String valeur, int max) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        return valeur.length() > max ? valeur.substring(0, max) : valeur;
    }

    /** Empreinte navigateur facultative (signal souple) : ignorée si elle n'a pas la forme d'un hash. */
    private static String empreinteValide(String empreinte) {
        return empreinte != null && EMPREINTE_VALIDE.matcher(empreinte).matches() ? empreinte : null;
    }
}
