package bf.laterrasse.nks.gateway.social;

import bf.laterrasse.nks.config.SocialVoteProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Optional;

/**
 * Relève les compteurs likes/commentaires d'une publication via Facebook Graph API.
 * Nécessite un Page Access Token longue durée avec la permission `pages_read_engagement`
 * (Standard Access suffit tant que le compte utilisé a un rôle Admin/Développeur/Testeur
 * sur l'app Meta — pas d'App Review requis dans ce cas). Voir README §Configuration Meta
 * (Facebook) pour la procédure complète d'obtention du token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FacebookGraphSocialVoteProvider implements SocialVoteProvider {

    private final SocialVoteProperties properties;

    @Override
    public String getNomPlateforme() {
        return "FACEBOOK";
    }

    @Override
    public Optional<EngagementSocial> relever(String postId) {
        SocialVoteProperties.Facebook config = properties.getFacebook();
        if (config.getAccessToken() == null || config.getAccessToken().isBlank()) {
            log.debug("FACEBOOK_ACCESS_TOKEN non configuré — relevé social Facebook ignoré pour {}", postId);
            return Optional.empty();
        }
        try {
            WebClient client = WebClient.create("https://graph.facebook.com/" + config.getGraphApiVersion());
            String champLikes = config.isCompterToutesReactions()
                    ? "reactions.summary(total_count)" : "likes.summary(true)";

            // .exchangeToMono (et non .retrieve()) : Graph API renvoie souvent un corps JSON
            // {"error": {...}} exploitable même avec un statut HTTP 4xx — .retrieve() lèverait
            // une WebClientResponseException avant qu'on ait pu lire ce corps, et on perdrait
            // le vrai message/code d'erreur Facebook (cas vécu : "400 Bad Request" sans détail
            // alors que le corps contenait la cause précise).
            JsonNode response = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/" + postId)
                            .queryParam("fields", champLikes + ",comments.summary(true)")
                            .queryParam("access_token", config.getAccessToken())
                            .build())
                    .exchangeToMono(clientResponse -> clientResponse.bodyToMono(JsonNode.class))
                    .block();

            if (response == null) {
                return Optional.empty();
            }
            if (response.has("error")) {
                int code = response.path("error").path("code").asInt(-1);
                String message = response.path("error").path("message").asText("erreur inconnue");
                if (code == 190) {
                    log.error("Token Facebook invalide/expiré (post {}). Régénérer un Page Access Token "
                            + "longue durée — voir README §Configuration Meta (Facebook). Détail : {}", postId, message);
                } else {
                    log.warn("Erreur Graph API pour le post {} (code {}) : {}", postId, code, message);
                }
                return Optional.empty();
            }

            long likes = config.isCompterToutesReactions()
                    ? response.path("reactions").path("summary").path("total_count").asLong(0)
                    : response.path("likes").path("summary").path("total_count").asLong(0);
            long comments = response.path("comments").path("summary").path("total_count").asLong(0);
            String snapshotId = "FB-" + postId + "-" + Instant.now().getEpochSecond();

            return Optional.of(new EngagementSocial("FACEBOOK", postId, snapshotId, likes, comments));
        } catch (Exception e) {
            log.warn("Échec relevé Facebook pour le post {} : {}", postId, e.getMessage());
            return Optional.empty();
        }
    }
}
