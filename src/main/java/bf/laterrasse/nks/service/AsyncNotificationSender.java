package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.enums.Enums.CanalNotification;
import bf.laterrasse.nks.domain.enums.Enums.StatutEnvoiNotification;
import bf.laterrasse.nks.gateway.email.EmailGateway;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Bean séparé requis pour que @Async soit honoré — Spring AOP ne peut pas intercepter
 * les appels en self-invocation dans le même bean (contournement ADR-08).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncNotificationSender {

    private static final short MAX_TENTATIVES = 3;

    private final SmsGateway smsGateway;
    private final EmailGateway emailGateway;
    private final NotificationRepository notificationRepository;

    @Async
    @Transactional
    public void tenterEnvoi(Notification notification) {
        if (notification.getNbTentatives() >= MAX_TENTATIVES) {
            return;
        }
        try {
            String reference;
            if (notification.getCanal() == CanalNotification.SMS) {
                reference = smsGateway.envoyer(notification.getTelephoneDestinataire(), notification.getCorpsMessage());
            } else if (notification.getCanal() == CanalNotification.EMAIL) {
                emailGateway.envoyer(notification.getEmailDestinataire(), notification.getSujet(), notification.getCorpsMessage());
                reference = null;
            } else {
                reference = null;
            }
            notification.setStatutEnvoi(StatutEnvoiNotification.ENVOYE);
            notification.setDateEnvoi(Instant.now());
            notification.setReferenceExterne(reference);
        } catch (Exception e) {
            log.warn("Échec tentative {}/{} d'envoi notification {} : {}",
                    notification.getNbTentatives() + 1, MAX_TENTATIVES, notification.getId(), e.getMessage());
            notification.setStatutEnvoi(StatutEnvoiNotification.ECHOUE);
        } finally {
            notification.setNbTentatives((short) (notification.getNbTentatives() + 1));
            try {
                notificationRepository.save(notification);
            } catch (Exception e) {
                log.error("Impossible de persister le statut de la notification {} : {}", notification.getId(), e.getMessage());
            }
        }
    }
}
