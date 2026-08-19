package bf.laterrasse.nks.job;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.enums.Enums.StatutEnvoiNotification;
import bf.laterrasse.nks.repository.NotificationRepository;
import bf.laterrasse.nks.service.AsyncNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** US-37 : jusqu'à 3 tentatives d'envoi, relancées toutes les 5 minutes. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryJob {

    private static final short MAX_TENTATIVES = 3;

    private final NotificationRepository notificationRepository;
    private final AsyncNotificationSender asyncSender;

    @Scheduled(fixedRate = 300_000)
    public void relancer() {
        var echouees = notificationRepository
                .findByStatutEnvoiAndNbTentativesLessThan(StatutEnvoiNotification.ECHOUE, MAX_TENTATIVES);

        for (Notification notification : echouees) {
            asyncSender.tenterEnvoi(notification.getId());
        }
        if (!echouees.isEmpty()) {
            log.info("{} notification(s) en échec relancée(s)", echouees.size());
        }
    }
}
