package bf.laterrasse.nks.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nks.media")
@Getter
@Setter
public class MediaProperties {

    private String provider = "cloudinary";
    private long maxPhotoSizeBytes = 5_242_880L;
    private long maxVideoSizeBytes = 104_857_600L; // 100 Mo — décision client
    private int videoMinDurationSeconds = 45;
    private int videoMaxDurationSeconds = 60;
    private long presignedUrlExpirationSeconds = 3600;

    @NestedConfigurationProperty
    private Cloudinary cloudinary = new Cloudinary();

    @Getter
    @Setter
    public static class Cloudinary {
        private String cloudName;
        private String apiKey;
        private String apiSecret;
    }
}
