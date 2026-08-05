package bf.laterrasse.nks.gateway.sms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du gateway SMS Africa's Talking.
 *
 * Deux tests distincts :
 *
 *  1. envoi_simule_sans_api_key() — test unitaire pur, aucun appel réseau, tourne toujours
 *     (y compris en CI). Vérifie le mode simulation quand apiKey est vide.
 *
 *  2. envoi_reel_sandbox() — test d'intégration qui appelle réellement l'API sandbox
 *     d'Africa's Talking. Désactivé par défaut (via @EnabledIfEnvironmentVariable) pour ne
 *     jamais casser `mvn test` chez quelqu'un qui n'a pas de clé — il ne s'exécute que si la
 *     variable d'environnement AT_TEST_API_KEY est définie.
 *
 * Pour lancer le test réel en local :
 *   1. Dashboard Africa's Talking → Settings → API Key, onglet "Sandbox" (pas "Live") →
 *      copier la clé.
 *   2. Ajouter le numéro de test dans Dashboard → Sandbox → Simulator (obligatoire : en
 *      sandbox, Africa's Talking n'envoie qu'aux numéros déclarés comme simulateurs, jamais
 *      à un vrai téléphone).
 *   3. Exporter les variables avant de lancer Maven (ne JAMAIS committer la clé dans le code) :
 *        macOS/Linux : export AT_TEST_API_KEY=xxxxxxxx ; export AT_TEST_PHONE=+22670000000
 *        PowerShell  : $env :AT_TEST_API_KEY="xxxxxxxx" ; $env:AT_TEST_PHONE="+22670000000"
 *   4. mvn test -Dtest=AfricasTalkingSmsGatewayTest
 */
class AfricasTalkingSmsGatewayTest {

    private static final String SENDER_ID = "LATERRASSE";

    @Test
    void envoi_simule_sans_api_key() {
        // apiKey vide → le gateway ne doit faire aucun appel réseau et retourner un id simulé.
        AfricasTalkingSmsGateway gateway = new AfricasTalkingSmsGateway("sandbox", "", SENDER_ID);

        String response = gateway.envoyer("+22676424845", "Test NKS simulation");

        assertThat(response).startsWith("SIMULATED-");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AT_TEST_API_KEY", matches = ".+")
    void envoi_reel_sandbox() {
        // AT_TEST_USERNAME : mettre le username exact affiché sur le dashboard AT à côté de
        // la clé (Settings → API Key). "sandbox" ne fonctionne QUE pour l'ancienne app de test
        // partagée par défaut — les apps créées avec le nouveau système (clés préfixées
        // "atsk_...") ont leur propre username, différent du literal "sandbox".
        String username = System.getenv().getOrDefault("AT_TEST_USERNAME", "sandbox");
        String apiKey = System.getenv("AT_TEST_API_KEY");
        String telephone = System.getenv().getOrDefault("AT_TEST_PHONE", "+22676424845");

        AfricasTalkingSmsGateway gateway = new AfricasTalkingSmsGateway(username, apiKey, SENDER_ID);

        String response = gateway.envoyer(telephone, "Test NKS sandbox");

        System.out.println("Réponse Africa's Talking (username=" + username + ") : " + response);
        // Un statut "InvalidSenderId"/"Recipients":[] ferait passer un simple .contains("SMSMessageData")
        // à tort (voir historique) — on vérifie donc qu'au moins un destinataire a réellement le statut "Success".
        assertThat(response).contains("\"status\":\"Success\"");
    }
}
