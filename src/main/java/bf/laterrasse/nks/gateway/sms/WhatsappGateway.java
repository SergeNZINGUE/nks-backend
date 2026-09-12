package bf.laterrasse.nks.gateway.sms;

import java.util.List;

/**
 * Abstraction gateway WhatsApp — HDR Stream impose des templates Meta pré-approuvés.
 * Le contenu est toujours un couple (clé de template + liste de variables ordonnées).
 */
public interface WhatsappGateway {
    /** @return référence externe (sid) fournie par le fournisseur. */
    String envoyer(String telephone, String template, List<String> variables);
}
