package bf.laterrasse.nks.gateway.sms;

/** Abstraction gateway SMS — implémentation courante : HDR Stream proxy (expéditeur "NKS 2026"). */
public interface SmsGateway {

    /** @return référence externe fournie par le fournisseur, pour traçabilité. */
    String envoyer(String telephone, String message);

    /** Normalise un numéro burkinabè vers E.164 (+226XXXXXXXX). */
    static String normaliserTelephone(String telephone) {
        if (telephone == null || telephone.isBlank()) return telephone;
        String t = telephone.trim();
        if (t.startsWith("+"))      return t;                    // déjà E.164
        if (t.startsWith("00226")) return "+" + t.substring(2); // 00226XXXXXXXX → +226XXXXXXXX
        if (t.startsWith("226"))   return "+" + t;              // 226XXXXXXXX   → +226XXXXXXXX
        return "+226" + t;                                       // XXXXXXXX      → +226XXXXXXXX
    }
}
