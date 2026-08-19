package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.CanalNotification;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * US-37/US-38 : notifications SMS + e-mail automatiques à chaque étape clé, 3 tentatives
 * max (RM au §14 / contrainte nb_tentatives <= 3). L'envoi effectif est délégué à
 * {@link AsyncNotificationSender} pour être réellement asynchrone (self-invocation
 * empêcherait @Async de fonctionner si la méthode restait dans ce bean — ADR-08).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AsyncNotificationSender asyncSender;

    @Transactional
    public void envoyerSms(Utilisateur destinataire, String telephone, TypeNotification type, String message) {
        Notification notification = Notification.builder()
                .utilisateur(destinataire)
                .telephoneDestinataire(telephone)
                .canal(CanalNotification.SMS)
                .typeNotification(type)
                .corpsMessage(message)
                .build();
        notificationRepository.save(notification);
        UUID id = notification.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncSender.tenterEnvoi(id);
            }
        });
    }

    @Transactional
    public void envoyerEmail(Utilisateur destinataire, String email, TypeNotification type, String sujet, String corpsHtml) {
        Notification notification = Notification.builder()
                .utilisateur(destinataire)
                .emailDestinataire(email)
                .canal(CanalNotification.EMAIL)
                .typeNotification(type)
                .sujet(sujet)
                .corpsMessage(corpsHtml)
                .build();
        notificationRepository.save(notification);
        UUID id = notification.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                asyncSender.tenterEnvoi(id);
            }
        });
    }

    /** Envoie SMS + e-mail pour les événements clés qui doivent doubler les deux canaux (US-04, US-37). */
    public void envoyerSmsEtEmail(Utilisateur destinataire, String telephone, String email,
                                   TypeNotification type, String smsMessage, String sujetEmail, String corpsEmail) {
        if (telephone != null) {
            envoyerSms(destinataire, telephone, type, smsMessage);
        }
        if (email != null) {
            envoyerEmail(destinataire, email, type, sujetEmail, corpsEmail);
        }
    }
}
