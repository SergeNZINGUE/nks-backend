package bf.laterrasse.nks.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "nks.social-votes")
@Getter
@Setter
public class SocialVoteProperties {

    private String provider = "facebook-tiktok-api";
    private BigDecimal likePoints = new BigDecimal("0.25");
    private BigDecimal commentPoints = new BigDecimal("0.75");

    @NestedConfigurationProperty
    private Facebook facebook = new Facebook();

    @NestedConfigurationProperty
    private Tiktok tiktok = new Tiktok();

    @Getter
    @Setter
    public static class Facebook {
        private String pageId;
        /** Page Access Token longue durée (voir README §Configuration Meta / Facebook). */
        private String accessToken;
        /** Chaque version Graph API n'est garantie que ~2 ans — vérifier developers.facebook.com/docs/graph-api/guides/versioning. */
        private String graphApiVersion = "v26.0";
        /**
         * false (par défaut) : compte uniquement les réactions "J'aime" classiques via
         * l'edge /likes, conformément au libellé RM-19 du rapport ("un like vaut 0,25 pt").
         * true : compte TOUTES les réactions (Like/Love/Haha/Wow/Sad/Angry/Care) via
         * reactions.summary(total_count) — Meta recommande cet edge pour un total
         * d'engagement fidèle à ce qu'affiche Facebook publiquement. À activer si le
         * client considère qu'une réaction quelconque doit compter comme un "like".
         */
        private boolean compterToutesReactions = false;
    }

    @Getter
    @Setter
    public static class Tiktok {
        private String clientKey;
        private String clientSecret;
        private String accessToken;
    }
}
