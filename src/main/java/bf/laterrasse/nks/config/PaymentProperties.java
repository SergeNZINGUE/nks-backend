package bf.laterrasse.nks.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nks.payment")
@Getter
@Setter
public class PaymentProperties {

    private String provider = "ligdicash";
    private int votePriceFcfa = 100;
    private int prereservationTimeoutMinutes = 15;

    @NestedConfigurationProperty
    private LigdiCash ligdicash = new LigdiCash();

    @Getter
    @Setter
    public static class LigdiCash {
        private String baseUrl;
        private String apiKey;
        private String apiSecret;
        private String webhookSecret;
        private String callbackBaseUrl;
    }
}
