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
    public String construireEmailHtml(String prenom, String titre, String contenuHtml, String ctaLabel, String ctaUrl) {
        String bouton = (ctaLabel != null && ctaUrl != null) ? """
        <tr>
          <td align="center" style="padding:28px 40px 4px;">
            <a href="%s" style="display:inline-block;padding:14px 34px;background:#C9A227;color:#0D0D1E;font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:700;text-decoration:none;border-radius:999px;letter-spacing:.3px;">%s</a>
          </td>
        </tr>
        """.formatted(ctaUrl, ctaLabel) : "";

        return """
        <!DOCTYPE html>
        <html lang="fr"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;padding:0;background:#0D0D1E;">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#0D0D1E;">
          <tr><td align="center" style="padding:24px 12px;">
            <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%;background:#1A1A2E;border:1px solid #3A3A5C;border-radius:20px;overflow:hidden;">
              <tr><td align="center" style="padding:40px 40px 0;">
                <img src="https://res.cloudinary.com/uzonwmij/image/upload/w_140/v1787486658/nks/email-logo.png" width="140" alt="Night Karaoke Stars" style="display:block;border:0;outline:none;">
                <div style="width:56px;height:2px;background:#C9A227;margin:24px auto 0;"></div>
              </td></tr>
              <tr><td align="center" style="padding:24px 40px 0;">
                <h1 style="margin:0;font-family:'Playfair Display',Georgia,'Times New Roman',serif;font-weight:700;font-size:23px;letter-spacing:.3px;color:#E8C04A;">%s</h1>
              </td></tr>
              <tr><td style="padding:20px 40px 0;font-family:Arial,Helvetica,sans-serif;font-size:15px;line-height:1.6;color:#C8C8D0;">
                <p style="margin:0 0 16px;">Bonjour <strong style="color:#FFFFFF;">%s</strong>,</p>
                %s
              </td></tr>
              %s
              <tr><td align="center" style="padding:36px 40px 32px;">
                <div style="width:100%%;height:1px;background:#3A3A5C;margin:0 0 24px;"></div>
                <p style="margin:0;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:1.6;color:#7A7A8C;">
                  Night Karaoke Stars — La Terrasse, Ouagadougou<br>
                  Cet e-mail a été envoyé automatiquement, merci de ne pas y répondre.
                </p>
              </td></tr>
            </table>
          </td></tr>
        </table>
        </body></html>
        """.formatted(titre, prenom, contenuHtml, bouton);
    }

    /** Encadré mis en avant (code candidat, mot de passe temporaire, etc.) — usage optionnel. */
    public String encadre(String valeur, boolean mono) {
        String police = mono ? "'Courier New',Courier,monospace" : "Arial,Helvetica,sans-serif";
        return """
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#252540;border-left:3px solid #C9A227;border-radius:8px;margin:4px 0 0;">
          <tr><td style="padding:14px 18px;font-family:%s;font-size:17px;color:#FFFFFF;font-weight:700;word-break:break-all;">%s</td></tr>
        </table>
        """.formatted(police, valeur);
    }
}
