package bf.laterrasse.nks.gateway.social;

import java.util.Optional;

/**
 * Décision client : intégration API réelle (remplace la saisie manuelle envisagée en H3
 * du rapport). Chaque candidat publie un post officiel sur la page/compte NKS ; le
 * système relève périodiquement le nombre de likes et de commentaires de ce post.
 *
 * ⚠️ Nécessite côté client : création d'une app Meta (Facebook Graph API, permission
 * pages_read_engagement + Meta App Review) et d'une app TikTok for Developers avec accès
 * à l'API Display/Research selon disponibilité. Ces démarches d'approbation prennent
 * généralement plusieurs semaines — à lancer le plus tôt possible (cf. README §Décisions).
 */
public interface SocialVoteProvider {

    String getNomPlateforme();

    Optional<EngagementSocial> relever(String postId);
}
