package bf.laterrasse.nks.job;

import bf.laterrasse.nks.repository.OtpTicketVerificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Correctif audit sécurité (IDOR billetterie) : purge les codes OTP expirés depuis plus
 * d'1h (même pattern que {@link NotificationRetryJob}). Ne conserve jamais un code
 * exploitable au-delà de sa durée de vie utile.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OtpPurgeJob {

    private final OtpTicketVerificationRepository otpTicketVerificationRepository;

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void purger() {
        Instant seuil = Instant.now().minus(1, ChronoUnit.HOURS);
        long supprimes = otpTicketVerificationRepository.deleteByExpireAtBefore(seuil);
        if (supprimes > 0) {
            log.info("{} code(s) OTP billetterie expiré(s) purgé(s)", supprimes);
        }
    }
}
