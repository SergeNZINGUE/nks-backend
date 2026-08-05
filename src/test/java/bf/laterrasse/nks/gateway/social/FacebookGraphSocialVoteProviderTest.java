package bf.laterrasse.nks.gateway.social;

import bf.laterrasse.nks.config.SocialVoteProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du gateway de votes sociaux Facebook Graph API.
 *
 * Deux tests distincts :
 *
 *  1. relever_sans_token_configure() — test unitaire pur, aucun appel réseau, tourne toujours
 *     (y compris en CI). Vérifie que sans access token, le provider renvoie Optional.empty()
 *     au lieu de planter.
 *
 *  2. relever_post_reel() — test d'intégration qui appelle réellement Facebook Graph API sur
 *     un vrai post de la Page NKS. Désactivé par défaut (via @EnabledIfEnvironmentVariable) —
 *     il ne s'exécute que si FB_TEST_ACCESS_TOKEN est défini.
 *
 * Pour lancer le test réel en local (voir README §"Configuration Meta / Facebook" pour la
 * procédure complète d'obtention du token) :
 *   1. Récupérer le Page Access Token longue durée (étapes 1 à 4 du README).
 *   2. Publier (ou utiliser) un post existant sur la Page, et récupérer son ID via
 *      GET /{page-id}/posts dans l'Explorateur Graph API, ou dans l'URL du post.
 *   3. Exporter les variables avant de lancer Maven (ne JAMAIS committer le token dans le code) :
 *        PowerShell : $env:FB_TEST_ACCESS_TOKEN = "ton_page_access_token_longue_duree"
 *                     $env:FB_TEST_POST_ID = "123456789_987654321"
 *   4. mvn test -Dtest=FacebookGraphSocialVoteProviderTest
 */
class FacebookGraphSocialVoteProviderTest {

    @Test
    void relever_sans_token_configure() {
        SocialVoteProperties properties = new SocialVoteProperties();
        // accessToken reste null par défaut → aucun appel réseau ne doit être tenté.
        FacebookGraphSocialVoteProvider provider = new FacebookGraphSocialVoteProvider(properties);

        Optional<EngagementSocial> resultat = provider.relever("un-post-id-quelconque");

        assertThat(resultat).isEmpty();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "FB_TEST_ACCESS_TOKEN", matches = ".+")
    void relever_post_reel() {
        String accessToken = System.getenv("FB_TEST_ACCESS_TOKEN");
        String postId = System.getenv("FB_TEST_POST_ID");
        assertThat(postId)
                .as("FB_TEST_POST_ID doit être défini (ID d'un vrai post de la Page NKS)")
                .isNotBlank();

        SocialVoteProperties properties = new SocialVoteProperties();
        properties.getFacebook().setAccessToken(accessToken);
        // graphApiVersion garde la valeur par défaut de SocialVoteProperties (v26.0).

        FacebookGraphSocialVoteProvider provider = new FacebookGraphSocialVoteProvider(properties);

        Optional<EngagementSocial> resultat = provider.relever(postId);

        System.out.println("Relevé Facebook pour " + postId + " : " + resultat);

        assertThat(resultat).isPresent();
        assertThat(resultat.get().plateforme()).isEqualTo("FACEBOOK");
        assertThat(resultat.get().nombreLikes()).isGreaterThanOrEqualTo(0);
        assertThat(resultat.get().nombreCommentaires()).isGreaterThanOrEqualTo(0);
    }
}
