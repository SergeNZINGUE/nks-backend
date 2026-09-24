package bf.laterrasse.nks.exception;

/**
 * Extrait le NOM d'une contrainte violée depuis une exception de persistance, pour journaliser sans jamais
 * recopier le message SQL (qui contient les valeurs de la ligne, ex. un numéro de téléphone complet).
 */
public final class ContrainteBd {

    private ContrainteBd() {
    }

    public static String nom(Throwable erreur) {
        for (Throwable t = erreur; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return "inconnue";
    }
}
