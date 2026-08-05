package bf.laterrasse.nks.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nks.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Chemin classpath ou fichier vers la clé privée RSA (PEM, PKCS#8). */
    private Resource privateKeyPath;

    /** Chemin classpath ou fichier vers la clé publique RSA (PEM, X.509). */
    private Resource publicKeyPath;

    private long accessTokenExpirationMinutes = 60;

    private long refreshTokenExpirationDays = 7;
}
