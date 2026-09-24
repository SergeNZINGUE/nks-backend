package bf.laterrasse.nks.service;

import bf.laterrasse.nks.exception.AppareilDejaUtiliseException;
import bf.laterrasse.nks.exception.AppareilInconnuException;
import bf.laterrasse.nks.repository.AppareilVoteSurPlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (Mockito, sans contexte Spring ni base) du blocage "dur" par appareil :
 * jeton signe HMAC (valide / falsifie / absent / tronque / mal forme), hash stable, et regle
 * "un appareil = un billet par soiree". La concurrence reelle est couverte dans
 * AppareilVoteIntegrationTest, contre un vrai PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class AppareilVoteServiceTest {

    private static final String SECRET = "secret-de-test-appareil-vote-0123456789";

    @Mock private AppareilVoteSurPlaceRepository repository;

    private AppareilVoteService service;

    private final UUID soireeId = UUID.randomUUID();
    private final UUID ticketA = UUID.randomUUID();
    private final UUID ticketB = UUID.randomUUID();
    private final UUID appareil = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AppareilVoteService(repository, SECRET, "test");
    }

    private AppareilVoteService.ContexteAppareil ctx() {
        return new AppareilVoteService.ContexteAppareil("peu-importe", "10.0.0.1", "agent", null);
    }

    // ------------------------------------------------------------------ jeton

    @Test
    @DisplayName("Un jeton emis est de la forme uuid.signature et se verifie vers le meme uuid")
    void jetonEmis_estValide_etRestitueLUuid() {
        String jeton = service.emettre("10.0.0.1");

        assertThat(jeton).matches("^[0-9a-f\\-]{36}\\.[A-Za-z0-9_\\-]+$");
        UUID uuid = service.verifierJeton(jeton);
        assertThat(uuid.toString()).isEqualTo(jeton.substring(0, jeton.indexOf('.')));
        assertThat(service.lireJetonSiValide(jeton)).contains(uuid);
    }

    @Test
    @DisplayName("Deux jetons emis successivement sont distincts")
    void deuxJetons_sontDistincts() {
        assertThat(service.emettre("10.0.0.1")).isNotEqualTo(service.emettre("10.0.0.1"));
    }

    @Test
    @DisplayName("Signature falsifiee (un caractere modifie) => APPAREIL_INCONNU")
    void signatureFalsifiee_estRefusee() {
        String jeton = service.emettre("10.0.0.1");
        char dernier = jeton.charAt(jeton.length() - 1);
        String falsifie = jeton.substring(0, jeton.length() - 1) + (dernier == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> service.verifierJeton(falsifie)).isInstanceOf(AppareilInconnuException.class);
        assertThat(service.lireJetonSiValide(falsifie)).isEmpty();
    }

    @Test
    @DisplayName("Uuid remplace en gardant la signature d'un autre jeton => refuse")
    void uuidSubstitue_estRefuse() {
        String jeton = service.emettre("10.0.0.1");
        String signature = jeton.substring(jeton.indexOf('.') + 1);
        String forge = UUID.randomUUID() + "." + signature;

        assertThatThrownBy(() -> service.verifierJeton(forge)).isInstanceOf(AppareilInconnuException.class);
    }

    @Test
    @DisplayName("Jeton signe avec un AUTRE secret => refuse")
    void jetonSigneAvecAutreSecret_estRefuse() {
        AppareilVoteService autre = new AppareilVoteService(repository, "un-tout-autre-secret-0123456789ab", "test");
        String jetonEtranger = autre.emettre("10.0.0.1");

        assertThatThrownBy(() -> service.verifierJeton(jetonEtranger)).isInstanceOf(AppareilInconnuException.class);
    }

    @Test
    @DisplayName("Jeton absent, vide ou blanc => APPAREIL_INCONNU")
    void jetonAbsent_estRefuse() {
        assertThatThrownBy(() -> service.verifierJeton(null)).isInstanceOf(AppareilInconnuException.class);
        assertThatThrownBy(() -> service.verifierJeton("")).isInstanceOf(AppareilInconnuException.class);
        assertThatThrownBy(() -> service.verifierJeton("   ")).isInstanceOf(AppareilInconnuException.class);
        assertThat(service.lireJetonSiValide(null)).isEmpty();
    }

    @Test
    @DisplayName("Jeton tronque ou mal forme (sans point, signature vide, uuid vide, deux points, uuid invalide, trop long) => refuse")
    void jetonMalForme_estRefuse() {
        String jeton = service.emettre("10.0.0.1");
        String uuid = jeton.substring(0, jeton.indexOf('.'));
        String signature = jeton.substring(jeton.indexOf('.') + 1);

        String[] invalides = {
                uuid,                                          // pas de point
                uuid + ".",                                    // signature vide
                "." + signature,                               // uuid vide
                jeton + "." + signature,                       // deux points
                "pas-un-uuid." + signature,                    // uuid non parsable
                jeton.substring(0, jeton.length() - 10),       // signature tronquee
                jeton.substring(0, 20),                        // uuid tronque
                uuid + "." + "A".repeat(300),                  // trop long
        };
        for (String invalide : invalides) {
            assertThatThrownBy(() -> service.verifierJeton(invalide))
                    .as("jeton %s", invalide.length() > 60 ? invalide.substring(0, 60) + "..." : invalide)
                    .isInstanceOf(AppareilInconnuException.class);
            assertThat(service.lireJetonSiValide(invalide)).isEmpty();
        }
    }

    @Test
    @DisplayName("Signature recalculee sur la forme canonique de l'uuid : majuscules + bonne signature = meme appareil, sans signature = refuse")
    void uuidMajuscules() {
        String jeton = service.emettre("10.0.0.1");
        String uuid = jeton.substring(0, jeton.indexOf('.'));
        String signature = jeton.substring(jeton.indexOf('.') + 1);

        assertThat(service.lireJetonSiValide(uuid.toUpperCase() + "." + signature)).contains(UUID.fromString(uuid));
        assertThat(service.lireJetonSiValide(uuid.toUpperCase() + ".AAAA")).isEmpty();
    }

    // ------------------------------------------------------------------ hash

    @Test
    @DisplayName("hash() : SHA-256 hexadecimal stable, distinct par uuid, jamais le jeton brut")
    void hash_estStable_etNeContientPasLeJeton() {
        String h1 = AppareilVoteService.hash(appareil);
        String h2 = AppareilVoteService.hash(appareil);

        assertThat(h1).isEqualTo(h2).matches("^[0-9a-f]{64}$");
        assertThat(h1).doesNotContain(appareil.toString());
        assertThat(AppareilVoteService.hash(UUID.randomUUID())).isNotEqualTo(h1);
    }

    // ------------------------------------------------------------------ configuration du secret

    @Test
    @DisplayName("Secret absent ou egal a la valeur de dev : refuse hors profils dev/test, tolere en dev/test")
    void secretParDefaut_refuseEnProd() {
        assertThatThrownBy(() -> new AppareilVoteService(repository, "", "prod")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AppareilVoteService(repository, null, "homol")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AppareilVoteService(repository, AppareilVoteService.DEFAULT_DEV_SECRET, "prod"))
                .isInstanceOf(IllegalStateException.class);

        assertThatCode(() -> new AppareilVoteService(repository, "", "dev")).doesNotThrowAnyException();
        assertThatCode(() -> new AppareilVoteService(repository, "", "test")).doesNotThrowAnyException();
        assertThatCode(() -> new AppareilVoteService(repository, "un-vrai-secret-de-prod-0123456789", "prod"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("(8) Secret de moins de 32 caracteres : refuse hors dev/test (31 refuse, 32 accepte), tolere en dev/test")
    void secretTropCourt_refuseEnProd() {
        assertThatThrownBy(() -> new AppareilVoteService(repository, "a".repeat(31), "prod"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AppareilVoteService(repository, "a".repeat(31), "homol"))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new AppareilVoteService(repository, "a".repeat(32), "prod")).doesNotThrowAnyException();
        assertThatCode(() -> new AppareilVoteService(repository, "court", "dev")).doesNotThrowAnyException();
        assertThatCode(() -> new AppareilVoteService(repository, "court", "test")).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ un appareil = un billet

    @Test
    @DisplayName("estLieAUnAutreBillet : false si jamais vu ou lie au MEME billet, true si lie a un autre billet")
    void estLieAUnAutreBillet() {
        String hash = AppareilVoteService.hash(appareil);
        when(repository.findTicketIdBySoireeAndHash(soireeId, hash)).thenReturn(Optional.empty());
        assertThat(service.estLieAUnAutreBillet(soireeId, ticketA, appareil)).isFalse();

        when(repository.findTicketIdBySoireeAndHash(soireeId, hash)).thenReturn(Optional.of(ticketA));
        assertThat(service.estLieAUnAutreBillet(soireeId, ticketA, appareil)).isFalse();
        assertThat(service.estLieAUnAutreBillet(soireeId, ticketB, appareil)).isTrue();
    }

    @Test
    @DisplayName("Premier vote de l'appareil : la ligne est inseree avec le HASH (jamais le jeton) et le billet")
    void premierVote_insereLaLigne() {
        String hash = AppareilVoteService.hash(appareil);
        when(repository.findTicketIdBySoireeAndHash(soireeId, hash)).thenReturn(Optional.empty());
        when(repository.insererSiAbsent(any(), eq(soireeId), eq(hash), eq(ticketA), anyString(), anyString(), isNull()))
                .thenReturn(1);

        service.enregistrerOuVerifier(soireeId, ticketA, appareil, ctx());

        verify(repository).insererSiAbsent(any(), eq(soireeId), eq(hash), eq(ticketA), eq("10.0.0.1"), eq("agent"), isNull());
    }

    @Test
    @DisplayName("Empreinte facultative ignoree si elle n'a pas la forme d'un hash ; ip et user-agent tronques")
    void empreinteEtChampsLibres_sontAssainis() {
        String hash = AppareilVoteService.hash(appareil);
        when(repository.findTicketIdBySoireeAndHash(soireeId, hash)).thenReturn(Optional.empty());
        when(repository.insererSiAbsent(any(), any(), anyString(), any(), any(), any(), any())).thenReturn(1);

        service.enregistrerOuVerifier(soireeId, ticketA, appareil, new AppareilVoteService.ContexteAppareil(
                "t", "1".repeat(80), "u".repeat(900), "<script>alert(1)</script>"));

        verify(repository).insererSiAbsent(any(), eq(soireeId), eq(hash), eq(ticketA),
                eq("1".repeat(45)), eq("u".repeat(500)), isNull());
    }

    @Test
    @DisplayName("Appareil deja lie au MEME billet (vote bonus) : accepte, aucune nouvelle insertion")
    void memeBillet_voteBonus_estAccepte() {
        String hash = AppareilVoteService.hash(appareil);
        when(repository.findTicketIdBySoireeAndHash(soireeId, hash)).thenReturn(Optional.of(ticketA));

        assertThatCode(() -> service.enregistrerOuVerifier(soireeId, ticketA, appareil, ctx())).doesNotThrowAnyException();

        verify(repository, never()).insererSiAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Appareil deja lie a un AUTRE billet : APPAREIL_DEJA_UTILISE, aucune insertion")
    void autreBillet_estRefuse() {
        String hash = AppareilVoteService.hash(appareil);
        when(repository.findTicketIdBySoireeAndHash(soireeId, hash)).thenReturn(Optional.of(ticketA));

        assertThatThrownBy(() -> service.enregistrerOuVerifier(soireeId, ticketB, appareil, ctx()))
                .isInstanceOf(AppareilDejaUtiliseException.class)
                .satisfies(e -> assertThat(((AppareilDejaUtiliseException) e).getCode()).isEqualTo("APPAREIL_DEJA_UTILISE"));

        verify(repository, never()).insererSiAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Course perdue (ON CONFLICT => 0 ligne) contre un AUTRE billet : APPAREIL_DEJA_UTILISE")
    void coursePerdue_contreAutreBillet_estRefusee() {
        String hash = AppareilVoteService.hash(appareil);
        when(repository.findTicketIdBySoireeAndHash(soireeId, hash))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ticketA));
        when(repository.insererSiAbsent(any(), any(), anyString(), any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.enregistrerOuVerifier(soireeId, ticketB, appareil, ctx()))
                .isInstanceOf(AppareilDejaUtiliseException.class);
    }

    @Test
    @DisplayName("Course perdue contre le MEME billet : accepte")
    void coursePerdue_contreMemeBillet_estAcceptee() {
        String hash = AppareilVoteService.hash(appareil);
        when(repository.findTicketIdBySoireeAndHash(soireeId, hash))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(ticketA));
        when(repository.insererSiAbsent(any(), any(), anyString(), any(), any(), any(), any())).thenReturn(0);

        assertThatCode(() -> service.enregistrerOuVerifier(soireeId, ticketA, appareil, ctx())).doesNotThrowAnyException();
    }
}
