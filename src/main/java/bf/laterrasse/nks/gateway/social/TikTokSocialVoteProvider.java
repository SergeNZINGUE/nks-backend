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
 * Relève les compteurs like_count/comment_count d'une vidéo via l'API TikTok for
 * Developers (Display API — champs exacts et endpoint à confirmer selon le niveau d'accès
 * accordé par TikTok, cf. SocialVoteProvider). Implémentation indicative en attendant la
 * validation de l'app cliente par TikTok.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TikTokSocialVoteProvider implements SocialVoteProvider {

    private final SocialVoteProperties properties;

    @Override
    public String getNomPlateforme() {
        return "TIKTOK";
    }

    @Override
    public Optional<EngagementSocial> relever(String videoId) {
        if (properties.getTiktok().getAccessToken() == null || properties.getTiktok().getAccessToken().isBlank()) {
            log.debug("TIKTOK_ACCESS_TOKEN non configuré — relevé social TikTok ignoré pour {}", videoId);
            return Optional.empty();
        }
        try {
            WebClient client = WebClient.create("https://open.tiktokapis.com/v2");
            JsonNode response = client.post()
                    .uri("/video/query/?fields=id,like_count,comment_count")
                    .header("Authorization", "Bearer " + properties.getTiktok().getAccessToken())
                    .bodyValue(java.util.Map.of("filters", java.util.Map.of("video_ids", java.util.List.of(videoId))))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                return Optional.empty();
            }
            JsonNode video = response.path("data").path("videos").isArray() && response.path("data").path("videos").size() > 0
                    ? response.path("data").path("videos").get(0) : null;
            if (video == null) {
                return Optional.empty();
            }
            long likes = video.path("like_count").asLong(0);
            long comments = video.path("comment_count").asLong(0);
            String snapshotId = "TT-" + videoId + "-" + Instant.now().getEpochSecond();

            return Optional.of(new EngagementSocial("TIKTOK", videoId, snapshotId, likes, comments));
        } catch (Exception e) {
            log.warn("Échec relevé TikTok pour la vidéo {} : {}", videoId, e.getMessage());
            return Optional.empty();
        }
    }
}
