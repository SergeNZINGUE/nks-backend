package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.OtpTicketVerification;
import bf.laterrasse.nks.dto.billetterie.OtpVerifierResponse;
import bf.laterrasse.nks.exception.OtpInvalideException;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.repository.OtpTicketVerificationRepository;
import bf.laterrasse.nks.repository.ReservationRepository;
import bf.laterrasse.nks.security.TicketAccessTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Correctif audit sécurité (IDOR billetterie) : quiconque connaissant un numéro de
 * téléphone ne doit plus pouvoir lire/annuler ses réservations sans prouver qu'il en a
 * la possession réelle (réception effective d'un SMS/WhatsApp). Émet, sur succès, un
 * jeton d'accès billets phone-wide avec scope ["read","cancel"] — {@link TicketAccessTokenService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpTicketVerificationService {

    private static final Duration DUREE_VALIDITE_CODE = Duration.ofMinutes(5);
    private static final Duration DUREE_VALIDITE_ACCESS_TOKEN = Duration.ofMinutes(30);
    private static final int SEUIL_ALERTE_OTP_JOURNALIER = 20;

    private final OtpTicketVerificationRepository otpTicketVerificationRepository;
    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final TicketAccessTokenService ticketAccessTokenService;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AtomicInteger compteurEnvoisJournalier = new AtomicInteger(0);
    private volatile LocalDate jourCompteur = LocalDate.now();

    /**
     * Toujours un succès côté appelant (réponse générique) — que le numéro ait des
     * réservations ou non, cf. audit : ne jamais exposer un oracle d'énumération de numéros.
     */
    @Transactional
    public void demander(String telephoneBrut, String ip) {
        String telephone = SmsGateway.normaliserTelephone(telephoneBrut);
        rateLimitService.verifierDemandeOtp(ip, telephone);

        boolean aDesReservations = !reservationRepository.findByTelephoneReservant(telephone).isEmpty();
        if (!aDesReservations) {
            return;
        }

        String code = genererCode();
        OtpTicketVerification otp = OtpTicketVerification.builder()
                .telephone(telephone)
                .codeHash(passwordEncoder.encode(code))
                .expireAt(Instant.now().plus(DUREE_VALIDITE_CODE))
                .build();
        otpTicketVerificationRepository.save(otp);

        notificationService.envoyerOtpSansPersistance(telephone,
                "Votre code de vérification NKS : " + code + " (valable 5 minutes, ne le partagez avec personne).");
        alerterSiQuotaJournalierDepasse();
    }

    @Transactional
    public OtpVerifierResponse verifier(String telephoneBrut, String code) {
        String telephone = SmsGateway.normaliserTelephone(telephoneBrut);
        OtpTicketVerification otp = otpTicketVerificationRepository
                .findFirstByTelephoneAndConsommeFalseOrderByDateCreationDesc(telephone)
                .orElse(null);

        if (otp == null || otp.isConsomme() || otp.getNbTentatives() >= RateLimitService.MAX_TENTATIVES
                || otp.getExpireAt().isBefore(Instant.now())) {
            throw new OtpInvalideException();
        }

        boolean codeValide = code != null && passwordEncoder.matches(code, otp.getCodeHash());
        if (!codeValide) {
            otp.setNbTentatives((short) (otp.getNbTentatives() + 1));
            if (otp.getNbTentatives() >= RateLimitService.MAX_TENTATIVES) {
                otp.setConsomme(true);
            }
            otpTicketVerificationRepository.save(otp);
            throw new OtpInvalideException();
        }

        otp.setConsomme(true);
        otpTicketVerificationRepository.save(otp);

        String accessToken = ticketAccessTokenService.emettre(
                telephone, Set.of("read", "cancel"), null, DUREE_VALIDITE_ACCESS_TOKEN);
        return new OtpVerifierResponse(accessToken, DUREE_VALIDITE_ACCESS_TOKEN.toSeconds());
    }

    private String genererCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    /**
     * Quota fournisseur HDR Stream = 100 messages/mois tous canaux confondus (cf.
     * NKS_WHATSAPP_TEMPLATES.md). Pas de nouvelle infra de quota : simple compteur en
     * mémoire remis à zéro chaque jour, avec juste un WARNING exploitable en cas de
     * consommation anormale — ne bloque jamais l'envoi.
     */
    private synchronized void alerterSiQuotaJournalierDepasse() {
        LocalDate aujourdHui = LocalDate.now();
        if (!aujourdHui.equals(jourCompteur)) {
            jourCompteur = aujourdHui;
            compteurEnvoisJournalier.set(0);
        }
        int total = compteurEnvoisJournalier.incrementAndGet();
        if (total > SEUIL_ALERTE_OTP_JOURNALIER) {
            log.warn("Plus de {} codes OTP billetterie envoyés aujourd'hui ({}) — quota fournisseur "
                    + "HDR Stream limité à 100 msg/mois tous canaux confondus, surveiller la consommation.",
                    SEUIL_ALERTE_OTP_JOURNALIER, total);
        }
    }
}
