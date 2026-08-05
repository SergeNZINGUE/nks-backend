package bf.laterrasse.nks.gateway.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailGateway implements EmailGateway {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public void envoyer(String destinataire, String sujet, String corpsHtml) {
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("SMTP non configuré — e-mail non envoyé (simulation) vers {} : {}", destinataire, sujet);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(destinataire);
            helper.setFrom(fromAddress, "Night Karaoke Stars");
            helper.setSubject(sujet);
            helper.setText(corpsHtml, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Échec envoi e-mail vers {} : {}", destinataire, e.getMessage());
            throw new IllegalStateException("Échec envoi e-mail", e);
        }
    }
}
