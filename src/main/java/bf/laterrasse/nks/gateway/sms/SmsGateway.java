package bf.laterrasse.nks.gateway.sms;

/** Abstraction SMS (Africa's Talking retenu — H10 ; Twilio en alternative). */
public interface SmsGateway {

    /** @return référence externe fournie par le fournisseur, pour traçabilité. */
    String envoyer(String telephone, String message);
}
