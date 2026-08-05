package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.CanalNotification;
import bf.laterrasse.nks.domain.enums.Enums.StatutEnvoiNotification;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
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
 * US-37/US-38 : notifications SMS + e-mail automatiques à chaque étape clé, 3 tentatives
 * max (RM au §14 / contrainte nb_tentatives <= 3). L'envoi effectif est asynchrone pour ne
 * jamais bloquer le flux métier appelant (ex : activation de profil après paiement).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final short MAX_TENTATIVES = 3;

    private final NotificationRepository notificationRepository;
    private final SmsGateway smsGateway;
    private final EmailGateway emailGateway;

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
        tenterEnvoi(notification);
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
        tenterEnvoi(notification);
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
                reference = null; // IN_APP : rien à envoyer, juste persisté
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
            notificationRepository.save(notification);
        }
    }
}
