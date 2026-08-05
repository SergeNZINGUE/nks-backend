package bf.laterrasse.nks.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Fournit la paire de clés RSA utilisée pour signer/vérifier les JWT (RS256, cf. §14.1
 * du rapport de conception). En production, les fichiers PEM doivent être fournis via
 * JWT_PRIVATE_KEY_PATH / JWT_PUBLIC_KEY_PATH (secret manager). En dev, si aucun fichier
 * n'est trouvé, une paire de clés éphémère est générée au démarrage (log WARN) — pratique
 * mais invalide les tokens à chaque redémarrage : NE JAMAIS faire ça en production.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class JwtKeyConfig {

    private final JwtProperties jwtProperties;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Bean
    public KeyPair jwtKeyPair() throws Exception {
        Resource privateKeyResource = jwtProperties.getPrivateKeyPath();
        Resource publicKeyResource = jwtProperties.getPublicKeyPath();

        if (privateKeyResource != null && privateKeyResource.exists()
                && publicKeyResource != null && publicKeyResource.exists()) {
            return loadKeyPairFromPem(privateKeyResource, publicKeyResource);
        }

        if ("prod".equalsIgnoreCase(activeProfile)) {
            throw new IllegalStateException(
                    "Clés JWT RS256 introuvables (JWT_PRIVATE_KEY_PATH / JWT_PUBLIC_KEY_PATH). "
                            + "Obligatoire en production — voir README.md § Sécurité / JWT.");
        }

        log.warn("Aucune clé JWT PEM trouvée — génération d'une paire RSA éphémère pour le "
                + "profil '{}'. Les tokens émis seront invalidés à chaque redémarrage. "
                + "NE JAMAIS utiliser ce mode en production.", activeProfile);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private KeyPair loadKeyPairFromPem(Resource privateKeyResource, Resource publicKeyResource) throws Exception {
        RSAPrivateKey privateKey = (RSAPrivateKey) readPrivateKey(privateKeyResource);
        RSAPublicKey publicKey = (RSAPublicKey) readPublicKey(publicKeyResource);
        return new KeyPair(publicKey, privateKey);
    }

    private PrivateKey readPrivateKey(Resource resource) throws IOException, GeneralSecurityException {
        String pem = readPemContent(resource, "PRIVATE KEY");
        byte[] decoded = Base64.getDecoder().decode(pem);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private PublicKey readPublicKey(Resource resource) throws IOException, GeneralSecurityException {
        String pem = readPemContent(resource, "PUBLIC KEY");
        byte[] decoded = Base64.getDecoder().decode(pem);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
    }

    private String readPemContent(Resource resource, String label) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes());
            return content
                    .replace("-----BEGIN " + label + "-----", "")
                    .replace("-----END " + label + "-----", "")
                    .replaceAll("\\s", "");
        }
    }
}
