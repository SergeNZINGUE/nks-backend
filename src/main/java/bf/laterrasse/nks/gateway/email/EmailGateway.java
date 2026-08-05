package bf.laterrasse.nks.gateway.email;

/** Abstraction e-mail (SMTP — Gmail SMTP ou Sendgrid). */
public interface EmailGateway {
    void envoyer(String destinataire, String sujet, String corpsHtml);
}
