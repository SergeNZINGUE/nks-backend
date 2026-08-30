package bf.laterrasse.nks.gateway.sms;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests du gateway SMS HDR Stream.
 */
class HdrStreamSmsGatewayTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    @DisplayName("1. Mode Simulation & Unitaires (MockWebServer)")
    class MockTests {

        @Test
        @DisplayName("Envoi simulé quand proxyUrl est vide")
        void envoi_simule_sans_proxy_url() {
            HdrStreamSmsGateway gateway = new HdrStreamSmsGateway("", "");

            String response = gateway.envoyer("+22670000000", "Test NKS simulation");

            assertThat(response).startsWith("SIMULATED-");
        }

        @Test
        @DisplayName("Succès - Envoi SMS avec headers et parsing sid")
        void envoi_succes() throws Exception {
            String mockResponseBody = """
                {
                    "success": true,
                    "sid": "SM123456789abcdef",
                    "sent_at": "2026-08-30T12:00:00Z"
                }
                """;

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponseBody));

            HdrStreamSmsGateway gateway = new HdrStreamSmsGateway(
                    mockWebServer.url("/api/nks/sms").toString(),
                    "test-proxy-api-key"
            );

            String sid = gateway.envoyer("+22670000000", "Test code NKS 123456");

            RecordedRequest request = mockWebServer.takeRequest();
            assertThat(request.getPath()).isEqualTo("/api/nks/sms");
            assertThat(request.getHeader("X-NKS-API-Key")).isEqualTo("test-proxy-api-key");
            assertThat(request.getHeader("Content-Type")).isEqualTo("application/json");

            String requestBody = request.getBody().readUtf8();
            assertThat(requestBody).contains("\"to\":\"+22670000000\"");
            assertThat(requestBody).contains("\"message\":\"Test code NKS 123456\"");

            assertThat(sid).isEqualTo("SM123456789abcdef");
        }

        @Test
        @DisplayName("Échec - 429 Quota mensuel atteint")
        void envoi_quota_atteint() {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"success\": false, \"error\": \"limit_reached\", \"detail\": \"Quota mensuel atteint\"}"));

            HdrStreamSmsGateway gateway = new HdrStreamSmsGateway(
                    mockWebServer.url("/api/nks/sms").toString(),
                    "test-proxy-api-key"
            );

            assertThatThrownBy(() -> gateway.envoyer("+22670000000", "Message test"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("429");
        }
    }

    @Nested
    @DisplayName("2. Test Réel HDR Stream (Live Proxy)")
    class LiveTests {

        private String recupererValeur(String... cles) {
            for (String cle : cles) {
                String val = System.getenv(cle);
                if (val != null && !val.isBlank()) return val;
            }
            try {
                java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
                if (java.nio.file.Files.exists(envPath)) {
                    for (String line : java.nio.file.Files.readAllLines(envPath)) {
                        String trim = line.trim();
                        for (String cle : cles) {
                            if (trim.startsWith(cle + "=")) {
                                return trim.substring((cle + "=").length()).trim();
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }

        @Test
        @DisplayName("Test envoi réel si credentials disponibles")
        void envoi_reel_si_disponible() {
            String proxyUrl = recupererValeur("NKS_SMS_URL", "NKS_SMS_PROXY_URL");
            String apiKey = recupererValeur("NKS_PROXY_API_KEY", "NKS_SMS_PROXY_API_KEY");
            String telephone = recupererValeur("NKS_SMS_TEST_PHONE");
            if (telephone == null || telephone.isBlank()) {
                telephone = "+22676424845";
            }

            org.junit.jupiter.api.Assumptions.assumeTrue(proxyUrl != null && !proxyUrl.isBlank(),
                    "NKS_SMS_URL non configuré");
            org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                    "NKS_PROXY_API_KEY non configuré");

            HdrStreamSmsGateway gateway = new HdrStreamSmsGateway(proxyUrl, apiKey);

            String message = "Test NKS " + System.currentTimeMillis();
            String sid = gateway.envoyer(telephone, message);

            System.out.println(">>> Succès envoi SMS HDR Stream ! SID : " + sid);
            assertThat(sid).isNotBlank();
        }
    }
}
