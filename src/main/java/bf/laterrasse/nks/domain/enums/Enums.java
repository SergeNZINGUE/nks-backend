package bf.laterrasse.nks.domain.enums;

/**
 * Regroupe les énumérations métier de la plateforme NKS.
 * Chaque valeur correspond exactement aux contraintes CHECK définies dans
 * V1__init_schema.sql — toute modification doit être répercutée des deux côtés.
 */
public final class Enums {

    private Enums() {
    }

    public enum RoleName {
        VISITEUR, CANDIDAT, VOTANT_PUBLIC, JURY, PARTENAIRE, ADMIN, SUPER_ADMIN, AGENT_ACCUEIL
    }

    public enum StatutUtilisateur {
        ACTIF, INACTIF, SUSPENDU
    }

    public enum StatutEdition {
        EN_PREPARATION, EN_COURS, TERMINEE, ARCHIVEE
    }

    public enum NomPhase {
        PRESELECTION, ELIMINATOIRES, DEMI_FINALE, FINALE
    }

    public enum StatutPhase {
        EN_ATTENTE, EN_COURS, TERMINEE
    }

    public enum StatutSoiree {
        PLANIFIEE, EN_COURS, TERMINEE, ANNULEE
    }

    public enum StatutProfilCandidat {
        EN_ATTENTE, ACTIF, SUSPENDU, ELIMINE, FINALISTE, GAGNANT
    }

    public enum StatutCandidature {
        EN_ATTENTE, VALIDEE, REJETEE, EN_ATTENTE_PAIEMENT, ACTIVE
    }

    public enum TypeMedia {
        PHOTO_PROFIL, CAPTURE_SOCIAL
    }

    public enum StatutMedia {
        EN_ATTENTE, VALIDE, MASQUE
    }

    public enum StatutVideo {
        EN_COURS_UPLOAD, DISPONIBLE, MASQUEE
    }

    public enum StatutJury {
        ACTIF, INACTIF
    }

    public enum TypeVote {
        EN_LIGNE_PAYANT, SOCIAL_LIKE, SOCIAL_COMMENTAIRE, PUBLIC_SUR_PLACE
    }

    public enum TypePaiement {
        INSCRIPTION, VOTE, BILLET
    }

    public enum StatutPaiement {
        PENDING, COMPLETED, FAILED, EXPIRED, REFUNDED
    }

    public enum OperateurMobileMoney {
        LIGDICASH, ORANGE_MONEY, MOOV_MONEY
    }

    public enum StatutQualification {
        QUALIFIE, ELIMINE, REPECHAGE, EN_ATTENTE
    }

    public enum NiveauPartenariat {
        TITRE, OR, ARGENT, PARTENAIRE
    }

    public enum StatutPartenaire {
        ACTIF, INACTIF
    }

    public enum NomCategorieTicket {
        STANDARD, VIP, PARTENAIRE
    }

    public enum StatutReservation {
        PENDING, CONFIRMEE, ANNULEE, EXPIREE
    }

    public enum StatutTicket {
        EMIS, ANNULE, UTILISE
    }

    public enum ResultatScan {
        VALIDE, INVALIDE, DEJA_UTILISE
    }

    public enum CanalNotification {
        SMS, EMAIL, IN_APP
    }

    public enum StatutEnvoiNotification {
        EN_ATTENTE, ENVOYE, ECHOUE
    }

    public enum TypeNotification {
        CANDIDATURE_RECUE, CANDIDATURE_VALIDEE, CANDIDATURE_REJETEE, PAIEMENT_CONFIRME,
        PAIEMENT_ECHOUE, CONVOCATION, RESULTAT_PHASE, REPECHAGE, BILLET_EMIS, PROFIL_ACTIVE
    }

    public enum TypeValeurParametre {
        INTEGER, DECIMAL, STRING, BOOLEAN
    }
}
