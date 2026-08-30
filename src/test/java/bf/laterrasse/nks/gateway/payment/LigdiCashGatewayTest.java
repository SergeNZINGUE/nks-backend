package bf.laterrasse.nks.gateway.payment;

import bf.laterrasse.nks.config.PaymentProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suite de tests pour LigdiCashGateway :
 *
 * 1. Tests unitaires simulés avec MockWebServer (aucun appel réseau sortant, ultra rapide).
 * 2. Tests de parsing de webhooks (JSON + form-urlencoded).
 * 3. Test d'intégration réel sandbox (activable avec LIGDICASH_TEST_API_KEY).
 */
class LigdiCashGatewayTest {

    private MockWebServer mockWebServer;
    private LigdiCashGateway gateway;
    private PaymentProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new PaymentProperties();
        properties.getLigdicash().setBaseUrl(mockWebServer.url("/pay/v01").toString());
        properties.getLigdicash().setApiKey("test-api-key-123");

        gateway = new LigdiCashGateway(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    @DisplayName("1. Initiation de paiement (createInvoice)")
    class InitiationPaiementTests {

        @Test
        @DisplayName("Succès - Retourne token et URL de redirection")
        void initierPaiement_succes() throws Exception {
            String mockResponseBody = """
                {
                    "response_code": "00",
                    "token": "tok_ligdicash_abc123",
                    "response_text": "Facture créée avec succès",
                    "redirect_url": "https://app.ligdicash.com/pay/v01/redirect/checkout-invoice/tok_ligdicash_abc123"
                }
                """;

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponseBody));

            InitiationPaiement resultat = gateway.initierPaiement(
                    new BigDecimal("5000"),
                    "+22670000000",
                    "idemp-uuid-12345",
                    "http://localhost:8082/api/v1/webhooks/ligdicash",
                    "http://localhost:4200/paiement/retour",
                    "http://localhost:4200/paiement/annule"
            );

            // Vérification de la requête envoyée
            RecordedRequest recordedRequest = mockWebServer.takeRequest();
            assertThat(recordedRequest.getPath()).isEqualTo("/pay/v01/redirect/checkout-invoice/create");
            assertThat(recordedRequest.getHeader("Apikey")).isEqualTo("test-api-key-123");
            assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer test-api-key-123");
            assertThat(recordedRequest.getHeader("Accept")).isEqualTo("application/json");

            String requestBody = recordedRequest.getBody().readUtf8();
            assertThat(requestBody).contains("\"total_amount\":5000");
            assertThat(requestBody).contains("\"devise\":\"XOF\"");
            assertThat(requestBody).contains("\"customer\":\"+22670000000\"");
            assertThat(requestBody).contains("\"external_id\":\"idemp-uuid-12345\"");

            // Vérification du résultat
            assertThat(resultat).isNotNull();
            assertThat(resultat.referenceOperateur()).isEqualTo("tok_ligdicash_abc123");
            assertThat(resultat.urlPaiement()).isEqualTo("https://app.ligdicash.com/pay/v01/redirect/checkout-invoice/tok_ligdicash_abc123");
        }

        @Test
        @DisplayName("Échec - Code de réponse différent de '00' (ex: Code02)")
        void initierPaiement_codeReponseInvalide_lanceException() {
            String mockResponseBody = """
                {
                    "response_code": "Code02",
                    "response_text": "Montant invalide",
                    "wiki": "https://docs.ligdicash.com/errors/code02"
                }
                """;

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponseBody));

            assertThatThrownBy(() -> gateway.initierPaiement(
                    new BigDecimal("0"),
                    "+22670000000",
                    "idemp-uuid-12345",
                    "http://localhost:8082/api/v1/webhooks/ligdicash",
                    "http://localhost:4200/paiement/retour",
                    "http://localhost:4200/paiement/annule"
            )).isInstanceOf(RuntimeException.class)
              .hasMessageContaining("Code02")
              .hasMessageContaining("Montant invalide");
        }

        @Test
        @DisplayName("Erreur HTTP - 500 Internal Server Error")
        void initierPaiement_erreurHttp_lanceException() {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\": \"Internal server error\"}"));

            assertThatThrownBy(() -> gateway.initierPaiement(
                    new BigDecimal("1000"),
                    "+22670000000",
                    "idemp-uuid-12345",
                    "http://localhost:8082/api/v1/webhooks/ligdicash",
                    "http://localhost:4200/paiement/retour",
                    "http://localhost:4200/paiement/annule"
            )).isInstanceOf(RuntimeException.class)
              .hasMessageContaining("500");
        }
    }

    @Nested
    @DisplayName("2. Confirmation de paiement (confirmInvoice)")
    class ConfirmationPaiementTests {

        @Test
        @DisplayName("Succès - Statut completed et response_code 00")
        void confirmerPaiement_succes() throws Exception {
            String mockResponseBody = """
                {
                    "response_code": "00",
                    "response_text": "Paiement effectué",
                    "status": "completed",
                    "total_amount": "5000",
                    "customer": "+22670000000",
                    "transaction_id": "TX_LIGDI_789"
                }
                """;

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponseBody));

            ConfirmationPaiement confirmation = gateway.confirmerPaiement("tok_ligdicash_abc123");

            RecordedRequest request = mockWebServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("GET");
            assertThat(request.getPath()).isEqualTo("/pay/v01/redirect/checkout-invoice/confirm/?invoiceToken=tok_ligdicash_abc123");

            assertThat(confirmation.succes()).isTrue();
            assertThat(confirmation.statutOperateur()).isEqualTo("completed");
            assertThat(confirmation.codeReponse()).isEqualTo("00");
            assertThat(confirmation.montant()).isEqualByComparingTo(new BigDecimal("5000"));
            assertThat(confirmation.telephonePayeur()).isEqualTo("+22670000000");
            assertThat(confirmation.motifRejet()).isNull();
        }

        @Test
        @DisplayName("Échec - Statut notcompleted (ex: solde insuffisant Code18)")
        void confirmerPaiement_soldeInsuffisant() {
            String mockResponseBody = """
                {
                    "response_code": "Code18",
                    "response_text": "Solde insuffisant sur le compte client",
                    "status": "notcompleted",
                    "amount": "5000",
                    "customer_phone_number": "+22670000000"
                }
                """;

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponseBody));

            ConfirmationPaiement confirmation = gateway.confirmerPaiement("tok_ligdicash_abc123");

            assertThat(confirmation.succes()).isFalse();
            assertThat(confirmation.statutOperateur()).isEqualTo("notcompleted");
            assertThat(confirmation.codeReponse()).isEqualTo("Code18");
            assertThat(confirmation.motifRejet()).isEqualTo("Solde insuffisant sur le compte client");
        }

        @Test
        @DisplayName("Erreur HTTP - Fallback propre sans lever d'exception bloquante")
        void confirmerPaiement_erreurHttp_retourneEchecProprement() {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setBody("Service Unavailable"));

            ConfirmationPaiement confirmation = gateway.confirmerPaiement("tok_ligdicash_abc123");

            assertThat(confirmation.succes()).isFalse();
            assertThat(confirmation.statutOperateur()).isEqualTo("error");
            assertThat(confirmation.motifRejet()).contains("503");
        }
    }

    @Nested
    @DisplayName("3. Extraction du token depuis le Webhook")
    class WebhookExtractionTests {

        @Test
        @DisplayName("Extraction depuis payload JSON")
        void extraireToken_depuisJson() {
            String jsonPayload = """
                {
                    "token": "tok_webhook_json_456",
                    "status": "completed",
                    "amount": 2500
                }
                """;

            String token = gateway.extraireTokenWebhook(jsonPayload);
            assertThat(token).isEqualTo("tok_webhook_json_456");
        }

        @Test
        @DisplayName("Extraction depuis payload Form URL Encoded")
        void extraireToken_depuisFormUrlEncoded() {
            String formPayload = "token=tok_webhook_form_789&status=completed&amount=1000";

            String token = gateway.extraireTokenWebhook(formPayload);
            assertThat(token).isEqualTo("tok_webhook_form_789");
        }

        @Test
        @DisplayName("Payload vide ou sans token -> retourne null")
        void extraireToken_payloadInvalide_retourneNull() {
            assertThat(gateway.extraireTokenWebhook(null)).isNull();
            assertThat(gateway.extraireTokenWebhook("   ")).isNull();
            assertThat(gateway.extraireTokenWebhook("{\"other_field\": \"value\"}")).isNull();
            assertThat(gateway.extraireTokenWebhook("status=completed&amount=1000")).isNull();
        }
    }

    @Nested
    @DisplayName("4. Remboursement (refund)")
    class RemboursementTests {

        @Test
        @DisplayName("Succès - Retourne référence de remboursement")
        void rembourserTransaction_succes() throws Exception {
            String mockResponseBody = """
                {
                    "response_code": "00",
                    "refund_id": "REF_LIGDI_999",
                    "response_text": "Remboursement initié"
                }
                """;

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(mockResponseBody));

            ResultatRemboursement resultat = gateway.rembourserTransaction("tok_ligdicash_abc123", new BigDecimal("5000"));

            RecordedRequest request = mockWebServer.takeRequest();
            assertThat(request.getPath()).isEqualTo("/pay/v01/redirect/checkout-invoice/refund");
            assertThat(request.getBody().readUtf8()).contains("\"amount\":5000");

            assertThat(resultat.succes()).isTrue();
            assertThat(resultat.referenceRemboursement()).isEqualTo("REF_LIGDI_999");
        }
    }

    @Nested
    @DisplayName("5. Test réel Sandbox / Production (Live API)")
    class IntegrationSandboxReelleTests {

        private String recupererValeur(String cle) {
            String val = System.getenv(cle);
            if (val != null && !val.isBlank()) return val;
            try {
                java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
                if (java.nio.file.Files.exists(envPath)) {
                    for (String line : java.nio.file.Files.readAllLines(envPath)) {
                        String trim = line.trim();
                        if (trim.startsWith(cle + "=")) {
                            return trim.substring((cle + "=").length()).trim();
                        }
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }

        @Test
        @DisplayName("Test réel d'initiation et vérification de la facture LigdiCash")
        void test_reel_creation_facture() {
            String apiKey = recupererValeur("LIGDICASH_API_KEY");
            String apiSecret = recupererValeur("LIGDICASH_API_SECRET");
            String baseUrl = recupererValeur("LIGDICASH_BASE_URL");
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://app.ligdicash.com/pay/v01";
            }

            org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                    "LIGDICASH_API_KEY non trouvé dans les variables d'environnement ou .env");

            PaymentProperties liveProps = new PaymentProperties();
            liveProps.getLigdicash().setBaseUrl(baseUrl);
            liveProps.getLigdicash().setApiKey(apiKey);
            liveProps.getLigdicash().setApiSecret(apiSecret);

            LigdiCashGateway liveGateway = new LigdiCashGateway(liveProps);

            InitiationPaiement initiation = liveGateway.initierPaiement(
                    new BigDecimal("100"),
                    "+22670000000",
                    "nks-test-" + System.currentTimeMillis(),
                    "http://localhost:8080/api/v1/webhooks/ligdicash",
                    "http://localhost:4200/paiement/retour",
                    "http://localhost:4200/paiement/annule"
            );

            System.out.println("=================================================");
            System.out.println(">>> SUCCÈS LIGDICASH REEL !");
            System.out.println(">>> Token généré         : " + initiation.referenceOperateur());
            System.out.println(">>> URL de paiement      : " + initiation.urlPaiement());
            System.out.println("=================================================");

            assertThat(initiation.referenceOperateur()).isNotBlank();
            assertThat(initiation.urlPaiement()).startsWith("http");

            // Test de confirmation pour ce token (statut doit être pending initialement)
            ConfirmationPaiement confirmation = liveGateway.confirmerPaiement(initiation.referenceOperateur());
            System.out.println(">>> Statut confirmInvoice : " + confirmation.statutOperateur() + " (code: " + confirmation.codeReponse() + ")");
            assertThat(confirmation.statutOperateur()).isNotBlank();
        }
    }
}
