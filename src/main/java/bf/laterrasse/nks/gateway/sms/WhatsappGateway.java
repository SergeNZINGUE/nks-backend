package bf.laterrasse.nks.gateway.sms;

/** Abstraction gateway WhatsApp — pendant de SmsGateway pour le canal WhatsApp HDR Stream. */
public interface WhatsappGateway {
    /** @return référence externe fournie par le fournisseur, pour traçabilité. */
    String envoyer(String telephone, String message);
}
