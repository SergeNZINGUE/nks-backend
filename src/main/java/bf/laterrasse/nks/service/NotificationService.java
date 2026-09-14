package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.CanalNotification;
import bf.laterrasse.nks.domain.enums.Enums.StatutEnvoiNotification;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.gateway.email.EmailGateway;
import bf.laterrasse.nks.gateway.email.EmailGateway.PieceJointe;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.gateway.sms.WhatsappGateway;
import bf.laterrasse.nks.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
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
    private final WhatsappGateway whatsappGateway;
    private final SmsGateway smsGateway;
    private final EmailGateway emailGateway;

    /**
     * WhatsApp est désormais le canal prioritaire pour toute notification "SMS" (demande
     * client du 13/09/2026) : tentative synchrone best-effort via {@link WhatsappGateway}
     * (même template générique "karaoke_info" que {@code VoteSurPlaceService}) ; le flux
     * SMS existant (persistance + retry async via {@link AsyncNotificationSender}) ne sert
     * plus que de fallback si l'envoi WhatsApp échoue ou est indisponible.
     */
    @Transactional
    public void envoyerSms(Utilisateur destinataire, String telephone, TypeNotification type, String message) {
        try {
            String reference = whatsappGateway.envoyer(
                    SmsGateway.normaliserTelephone(telephone), "karaoke_info", List.of(message));
            Notification notification = Notification.builder()
                    .utilisateur(destinataire)
                    .telephoneDestinataire(telephone)
                    .canal(CanalNotification.WHATSAPP)
                    .typeNotification(type)
                    .corpsMessage(message)
                    .statutEnvoi(StatutEnvoiNotification.ENVOYE)
                    .dateEnvoi(Instant.now())
                    .referenceExterne(reference)
                    .build();
            notificationRepository.save(notification);
            return;
        } catch (Exception e) {
            log.warn("Échec envoi WhatsApp pour {} (type {}), fallback SMS : {}",
                    telephone, type, e.getMessage());
        }

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

    /**
     * Correctif audit sécurité (OTP en clair persisté indéfiniment) : variante dédiée à
     * l'OTP billetterie ({@link OtpTicketVerificationService}) qui envoie le VRAI message
     * (avec le code en clair, WhatsApp puis repli SMS best-effort) mais NE PERSISTE AUCUNE
     * ligne dans {@code notifications} — contrairement à {@link #envoyerSms}, qui écrit
     * systématiquement {@code corpsMessage} en clair et sans expiration en base. Le code
     * OTP est déjà tracé (hashé, expiration 5 min, purge horaire via {@code OtpPurgeJob})
     * dans {@code otp_ticket_verifications} ; dupliquer ce texte en clair dans
     * {@code notifications.corps_message} annulerait l'intérêt du hashing (dump/accès DB
     * direct pendant la fenêtre de validité = code lisible sans effort). Volontairement pas
     * de retry automatique async ici (pas de ligne à rejouer) : acceptable pour ce cas
     * précis car l'utilisateur peut simplement redemander un code (flux rate-limité), à la
     * différence des autres notifications qui reposent sur {@link AsyncNotificationSender}.
     */
    public void envoyerOtpSansPersistance(String telephone, String message) {
        String telephoneNormalise = SmsGateway.normaliserTelephone(telephone);
        try {
            whatsappGateway.envoyer(telephoneNormalise, "karaoke_info", List.of(message));
            return;
        } catch (Exception e) {
            log.warn("Échec envoi WhatsApp OTP pour {}, repli SMS direct : {}", telephone, e.getMessage());
        }
        try {
            smsGateway.envoyer(telephoneNormalise, message);
        } catch (Exception e) {
            log.error("Échec envoi OTP (WhatsApp + SMS) pour {} : {}", telephone, e.getMessage());
        }
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

    /**
     * Surcharge avec pièces jointes (images QR des billets) — volontairement SYNCHRONE et
     * best-effort, SANS passer par {@link AsyncNotificationSender} : le flux de retry
     * existant (persistance + rejeu par {@code NotificationRetryJob}) ne rejoue qu'à partir
     * de {@code corps_message} en base, il n'y a nulle part où faire survivre des pièces
     * jointes binaires jusqu'à un retry différé sans complexité disproportionnée pour ce cas
     * précis. Même pattern que {@code VoteSurPlaceService.envoyerLienWhatsapp} : un échec est
     * loggé et NE DOIT JAMAIS faire échouer la réservation elle-même. En cas d'échec, la
     * notification est tout de même journalisée en {@code ECHOUE} avec 1 tentative : le
     * {@code NotificationRetryJob} la rejouera automatiquement (sans pièce jointe, via le
     * canal e-mail standard) comme filet de sécurité en dégradé.
     */
    public void envoyerEmail(Utilisateur destinataire, String email, TypeNotification type, String sujet,
                              String corpsHtml, List<PieceJointe> piecesJointes) {
        Notification notification = Notification.builder()
                .utilisateur(destinataire)
                .emailDestinataire(email)
                .canal(CanalNotification.EMAIL)
                .typeNotification(type)
                .sujet(sujet)
                .corpsMessage(corpsHtml)
                .build();
        try {
            emailGateway.envoyer(email, sujet, corpsHtml, piecesJointes);
            notification.setStatutEnvoi(StatutEnvoiNotification.ENVOYE);
            notification.setDateEnvoi(Instant.now());
        } catch (Exception e) {
            log.error("Échec envoi e-mail avec pièces jointes vers {} (type {}) : {}", email, type, e.getMessage());
            notification.setStatutEnvoi(StatutEnvoiNotification.ECHOUE);
        } finally {
            notification.setNbTentatives((short) 1);
            notificationRepository.save(notification);
        }
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
