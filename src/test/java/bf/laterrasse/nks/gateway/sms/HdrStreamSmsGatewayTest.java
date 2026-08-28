package bf.laterrasse.nks.gateway.sms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du gateway SMS HDR Stream.
 *
 *  1. envoi_simule_sans_proxy_url() — aucun appel réseau, proxy-url vide → retourne "SIMULATED-..."
 *  2. envoi_reel() — appelle le proxy réel. Nécessite NKS_SMS_PROXY_URL + NKS_SMS_PROXY_API_KEY.
 */
class HdrStreamSmsGatewayTest {

    @Test
    void envoi_simule_sans_proxy_url() {
        HdrStreamSmsGateway gateway = new HdrStreamSmsGateway("", "");

        String response = gateway.envoyer("+22670000000", "Test NKS simulation");

        assertThat(response).startsWith("SIMULATED-");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "NKS_SMS_PROXY_URL", matches = ".+")
    void envoi_reel() {
        String proxyUrl = System.getenv("NKS_SMS_PROXY_URL");
        String apiKey = System.getenv("NKS_SMS_PROXY_API_KEY");
        String telephone = System.getenv().getOrDefault("NKS_SMS_TEST_PHONE", "+22670000000");

        HdrStreamSmsGateway gateway = new HdrStreamSmsGateway(proxyUrl, apiKey);

        String sid = gateway.envoyer(telephone, "Test NKS 2026");

        System.out.println("sid HDR Stream : " + sid);
        assertThat(sid).isNotBlank();
    }
}
