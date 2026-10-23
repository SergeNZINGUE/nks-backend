package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Duo;
import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.Poule;
import bf.laterrasse.nks.domain.ResultatPhase;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.enums.Enums.NomPhase;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import bf.laterrasse.nks.domain.enums.Enums.StatutQualification;
import bf.laterrasse.nks.domain.enums.Enums.StatutSoiree;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.ClassementRepository;
import bf.laterrasse.nks.repository.DuoRepository;
import bf.laterrasse.nks.repository.NoteJuryRepository;
import bf.laterrasse.nks.repository.PhaseRepository;
import bf.laterrasse.nks.repository.ResultatPhaseRepository;
import bf.laterrasse.nks.repository.SnapshotVotesSoireeRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests (Mockito, no Spring context) for ClassementService qualification rules:
 * DEMI_FINALE top 2 per soiree (soiree of the duo), auto-repair of candidate profiles,
 * appliquerEliminationsStatutProfil on duo soirees, and non-regression of ELIMINATOIRES/FINALE.
 *
 * Points come exclusively from paid votes: total phase = 100, pointsMaxVotesEnLigne = 100,
 * so the totalPoints of each candidate equals the number of paid votes assigned to them.
 * ResultatPhaseRepository is backed by an in-memory map to simulate persistence between
 * the qualification step and the profile auto-repair step.
 */
@ExtendWith(MockitoExtension.class)
class ClassementServiceQualificationTest {

    @Mock private PhaseRepository phaseRepository;
    @Mock private CandidatRepository candidatRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private VoteService voteService;
    @Mock private NoteJuryRepository noteJuryRepository;
    @Mock private ResultatPhaseRepository resultatPhaseRepository;
    @Mock private ClassementRepository classementRepository;
    @Mock private AffectationPouleRepository affectationPouleRepository;
    @Mock private DuoRepository duoRepository;
    @Mock private SnapshotVotesSoireeRepository snapshotVotesSoireeRepository;
    @Mock private SoireeEventRepository soireeEventRepository;

    @InjectMocks private ClassementService classementService;

    private Edition edition;
    private final List<Candidat> candidats = new ArrayList<>();
    private final List<Duo> duos = new ArrayList<>();
    private final Map<UUID, Long> voixPayantes = new HashMap<>();
    private final Map<String, ResultatPhase> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        edition = Edition.builder().id(UUID.randomUUID()).nom("NKS 2026").build();

        lenient().when(candidatRepository.findAll()).thenAnswer(inv -> new ArrayList<>(candidats));
        lenient().when(voteService.totalVotesPayantsConfirmes(any())).thenReturn(100L);
        lenient().when(voteService.votesPayantsConfirmes(any(), any()))
                .thenAnswer(inv -> voixPayantes.getOrDefault(inv.<UUID>getArgument(0), 0L));
        lenient().when(resultatPhaseRepository.findByCandidatIdAndPhaseId(any(), any()))
                .thenAnswer(inv -> Optional.ofNullable(store.get(key(inv.getArgument(0), inv.getArgument(1)))));
        lenient().when(resultatPhaseRepository.saveAll(anyIterable())).thenAnswer(inv -> {
            Iterable<ResultatPhase> it = inv.getArgument(0);
            List<ResultatPhase> out = new ArrayList<>();
            for (ResultatPhase r : it) {
                store.put(key(r.getCandidat().getId(), r.getPhase().getId()), r);
                out.add(r);
            }
            return out;
        });
        lenient().when(duoRepository.findByPhaseId(any())).thenAnswer(inv -> {
            UUID pid = inv.getArgument(0);
            return duos.stream().filter(d -> d.getPhase().getId().equals(pid)).toList();
        });
        lenient().when(duoRepository.findBySoireeId(any())).thenAnswer(inv -> {
            UUID sid = inv.getArgument(0);
            return duos.stream().filter(d -> d.getSoiree() != null && d.getSoiree().getId().equals(sid)).toList();
        });
        lenient().when(duoRepository.findByPhaseIdAndCandidatId(any(), any())).thenAnswer(inv -> {
            UUID pid = inv.getArgument(0);
            UUID cid = inv.getArgument(1);
            return duos.stream()
                    .filter(d -> d.getPhase().getId().equals(pid))
                    .filter(d -> d.getCandidat1().getId().equals(cid) || d.getCandidat2().getId().equals(cid))
                    .findFirst();
        });
    }

    // ---------------------------------------------------------------- helpers

    private static String key(Object candidatId, Object phaseId) {
        return candidatId + "|" + phaseId;
    }

    private Phase phase(NomPhase nom) {
        Phase p = Phase.builder().id(UUID.randomUUID()).edition(edition).nom(nom).build();
        lenient().when(phaseRepository.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private SoireeEvent soiree(Phase phase, StatutSoiree statut) {
        return SoireeEvent.builder().id(UUID.randomUUID()).edition(edition).phase(phase).statut(statut).build();
    }

    private Candidat candidat(String code, long voix) {
        Candidat c = Candidat.builder().id(UUID.randomUUID()).edition(edition).codeCandidat(code)
                .statutProfil(StatutProfilCandidat.ACTIF).build();
        candidats.add(c);
        voixPayantes.put(c.getId(), voix);
        return c;
    }

    private Duo duo(Phase phase, SoireeEvent soiree, Candidat c1, Candidat c2) {
        Duo d = Duo.builder().id(UUID.randomUUID()).phase(phase).soiree(soiree).candidat1(c1).candidat2(c2).build();
        duos.add(d);
        return d;
    }

    private ResultatPhase resultatExistant(Candidat c, Phase phase, StatutQualification statut) {
        ResultatPhase r = ResultatPhase.builder().id(UUID.randomUUID()).candidat(c).phase(phase)
                .statutQualification(statut).build();
        store.put(key(c.getId(), phase.getId()), r);
        return r;
    }

    private static Map<String, StatutQualification> statutsParCode(List<ResultatPhase> resultats) {
        return resultats.stream().collect(Collectors.toMap(r -> r.getCandidat().getCodeCandidat(),
                ResultatPhase::getStatutQualification));
    }

    @SuppressWarnings("unchecked")
    private List<Candidat> profilsSauvegardes() {
        ArgumentCaptor<Iterable<Candidat>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(candidatRepository, atLeastOnce()).saveAll(captor.capture());
        List<Candidat> out = new ArrayList<>();
        captor.getAllValues().forEach(it -> it.forEach(out::add));
        return out;
    }

    // ---------------------------------------------------------------- DEMI_FINALE

    @Test
    @DisplayName("1. DEMI_FINALE soiree TERMINEE : top 2 QUALIFIE, autres ELIMINE, profils elimines basculent ACTIF->ELIMINE")
    void demiFinale_soireeTerminee_top2Qualifies_profilsElimines() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s = soiree(df, StatutSoiree.TERMINEE);
        Candidat a = candidat("A", 40), b = candidat("B", 30), c = candidat("C", 20), d = candidat("D", 10);
        // Duos mix top and bottom: qualification must be individual, not per duo
        duo(df, s, a, d);
        duo(df, s, b, c);

        List<ResultatPhase> res = classementService.calculerClassementPhase(df.getId());

        assertThat(res).hasSize(4);
        assertThat(res.get(0).getTotalPoints()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(statutsParCode(res)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "A", StatutQualification.QUALIFIE,
                "B", StatutQualification.QUALIFIE,
                "C", StatutQualification.ELIMINE,
                "D", StatutQualification.ELIMINE));

        assertThat(profilsSauvegardes()).extracting(Candidat::getCodeCandidat).containsExactlyInAnyOrder("C", "D");
        assertThat(a.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
        assertThat(b.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
        assertThat(c.getStatutProfil()).isEqualTo(StatutProfilCandidat.ELIMINE);
        assertThat(d.getStatutProfil()).isEqualTo(StatutProfilCandidat.ELIMINE);
    }

    @Test
    @DisplayName("1b. DEMI_FINALE : recalcul idempotent, pas de nouveau saveAll de profils au second passage")
    void demiFinale_recalculIdempotent() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s = soiree(df, StatutSoiree.TERMINEE);
        Candidat a = candidat("A", 40), b = candidat("B", 30), c = candidat("C", 20), d = candidat("D", 10);
        duo(df, s, a, b);
        duo(df, s, c, d);

        classementService.calculerClassementPhase(df.getId());
        // Après le 1er passage, C et D ont StatutProfilCandidat.ELIMINE → exclus du recalcul.
        // Le 2e passage ne retourne que les candidats ACTIF (A, B).
        List<ResultatPhase> res2 = classementService.calculerClassementPhase(df.getId());

        assertThat(statutsParCode(res2))
                .containsEntry("A", StatutQualification.QUALIFIE)
                .containsEntry("B", StatutQualification.QUALIFIE)
                .doesNotContainKey("C")
                .doesNotContainKey("D");
        // Les sauvegardes de profil ne se produisent qu'au 1er passage (C, D déjà ELIMINE au 2e).
        verify(candidatRepository, times(1)).saveAll(anyIterable());
    }

    @Test
    @DisplayName("2. DEMI_FINALE deux soirees (TERMINEE + EN_COURS) : classement independant, profils bascules seulement pour TERMINEE")
    void demiFinale_deuxSoirees_classementIndependant() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s1 = soiree(df, StatutSoiree.TERMINEE);
        SoireeEvent s2 = soiree(df, StatutSoiree.EN_COURS);
        // Soiree 2 candidates all outscore soiree 1: a global top 2 would wipe soiree 1
        Candidat a1 = candidat("A1", 8), b1 = candidat("B1", 6), c1 = candidat("C1", 4), d1 = candidat("D1", 2);
        Candidat a2 = candidat("A2", 30), b2 = candidat("B2", 25), c2 = candidat("C2", 15), d2 = candidat("D2", 10);
        duo(df, s1, a1, b1);
        duo(df, s1, c1, d1);
        duo(df, s2, a2, c2);
        duo(df, s2, b2, d2);

        List<ResultatPhase> res = classementService.calculerClassementPhase(df.getId());

        assertThat(statutsParCode(res)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "A1", StatutQualification.QUALIFIE, "B1", StatutQualification.QUALIFIE,
                "C1", StatutQualification.ELIMINE, "D1", StatutQualification.ELIMINE,
                "A2", StatutQualification.QUALIFIE, "B2", StatutQualification.QUALIFIE,
                "C2", StatutQualification.ELIMINE, "D2", StatutQualification.ELIMINE));

        assertThat(profilsSauvegardes()).extracting(Candidat::getCodeCandidat).containsExactlyInAnyOrder("C1", "D1");
        assertThat(c2.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
        assertThat(d2.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
    }

    @Test
    @DisplayName("3. DEMI_FINALE soiree PLANIFIEE : tous EN_ATTENTE (meme un QUALIFIE preexistant), aucun profil modifie")
    void demiFinale_soireePlanifiee_tousEnAttente() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s = soiree(df, StatutSoiree.PLANIFIEE);
        Candidat a = candidat("A", 40), b = candidat("B", 30), c = candidat("C", 20), d = candidat("D", 10);
        duo(df, s, a, b);
        duo(df, s, c, d);
        resultatExistant(a, df, StatutQualification.QUALIFIE);
        resultatExistant(d, df, StatutQualification.ELIMINE);

        List<ResultatPhase> res = classementService.calculerClassementPhase(df.getId());

        assertThat(res).extracting(ResultatPhase::getStatutQualification)
                .containsOnly(StatutQualification.EN_ATTENTE);
        verify(candidatRepository, never()).saveAll(anyIterable());
        assertThat(candidats).extracting(Candidat::getStatutProfil).containsOnly(StatutProfilCandidat.ACTIF);
    }

    @Test
    @DisplayName("3b. DEMI_FINALE soiree ANNULEE : tous EN_ATTENTE")
    void demiFinale_soireeAnnulee_tousEnAttente() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s = soiree(df, StatutSoiree.ANNULEE);
        duo(df, s, candidat("A", 40), candidat("B", 30));
        duo(df, s, candidat("C", 20), candidat("D", 10));

        List<ResultatPhase> res = classementService.calculerClassementPhase(df.getId());

        assertThat(res).extracting(ResultatPhase::getStatutQualification)
                .containsOnly(StatutQualification.EN_ATTENTE);
        verify(candidatRepository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("4. DEMI_FINALE : candidat de la meme edition sans duo dans la phase -> ELIMINE")
    void demiFinale_candidatSansDuo_elimine() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s = soiree(df, StatutSoiree.EN_COURS);
        duo(df, s, candidat("A", 40), candidat("B", 30));
        duo(df, s, candidat("C", 20), candidat("D", 10));
        Candidat sansDuo = candidat("X", 90); // highest score, but no duo

        List<ResultatPhase> res = classementService.calculerClassementPhase(df.getId());

        assertThat(statutsParCode(res)).containsEntry("X", StatutQualification.ELIMINE)
                .containsEntry("A", StatutQualification.QUALIFIE)
                .containsEntry("B", StatutQualification.QUALIFIE);
        // Profile auto-repair only covers duos of TERMINEE soirees
        assertThat(sansDuo.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
    }

    @Test
    @DisplayName("5. DEMI_FINALE : REPECHAGE conserve meme en 3e/4e position, profil non bascule")
    void demiFinale_repechageConserve() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s = soiree(df, StatutSoiree.TERMINEE);
        Candidat a = candidat("A", 40), b = candidat("B", 30), c = candidat("C", 20), d = candidat("D", 10);
        duo(df, s, a, b);
        duo(df, s, c, d);
        resultatExistant(c, df, StatutQualification.REPECHAGE);
        resultatExistant(d, df, StatutQualification.REPECHAGE);

        List<ResultatPhase> res = classementService.calculerClassementPhase(df.getId());

        assertThat(statutsParCode(res)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "A", StatutQualification.QUALIFIE, "B", StatutQualification.QUALIFIE,
                "C", StatutQualification.REPECHAGE, "D", StatutQualification.REPECHAGE));
        verify(candidatRepository, never()).saveAll(anyIterable());
        assertThat(c.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
        assertThat(d.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
    }

    @Test
    @DisplayName("5b. DEMI_FINALE PLANIFIEE : REPECHAGE non ecrase en EN_ATTENTE")
    void demiFinale_planifiee_repechageConserve() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s = soiree(df, StatutSoiree.PLANIFIEE);
        Candidat a = candidat("A", 40);
        duo(df, s, a, candidat("B", 30));
        resultatExistant(a, df, StatutQualification.REPECHAGE);

        List<ResultatPhase> res = classementService.calculerClassementPhase(df.getId());

        assertThat(statutsParCode(res)).containsEntry("A", StatutQualification.REPECHAGE)
                .containsEntry("B", StatutQualification.EN_ATTENTE);
    }

    // ---------------------------------------------------------------- Non-regression

    @Test
    @DisplayName("6a. Non-regression FINALE : tout EN_ATTENTE -> QUALIFIE, aucun profil modifie")
    void finale_toutQualifie() {
        Phase f = phase(NomPhase.FINALE);
        candidat("A", 40);
        candidat("B", 30);
        candidat("C", 0);

        List<ResultatPhase> res = classementService.calculerClassementPhase(f.getId());

        assertThat(res).hasSize(3).extracting(ResultatPhase::getStatutQualification)
                .containsOnly(StatutQualification.QUALIFIE);
        assertThat(res).extracting(ResultatPhase::getRang).containsExactly(1, 2, 3);
        verify(candidatRepository, never()).saveAll(anyIterable());
        verify(duoRepository, never()).findByPhaseId(any());
    }

    @Test
    @DisplayName("6b. Non-regression ELIMINATOIRES : top 2 par poule, sans poule -> ELIMINE, profils non modifies")
    void eliminatoires_top2ParPoule() {
        Phase el = phase(NomPhase.ELIMINATOIRES);
        SoireeEvent s = soiree(el, StatutSoiree.TERMINEE);
        Poule p1 = Poule.builder().id(UUID.randomUUID()).phase(el).soiree(s).nom("P1").build();
        Poule p2 = Poule.builder().id(UUID.randomUUID()).phase(el).soiree(s).nom("P2").build();
        Candidat a = candidat("A", 40), b = candidat("B", 30), c = candidat("C", 20);
        Candidat e = candidat("E", 5), f = candidat("F", 3), g = candidat("G", 1);
        Candidat sansPoule = candidat("X", 50);
        Map<UUID, Poule> pouleDe = new HashMap<>();
        for (Candidat x : List.of(a, b, c)) pouleDe.put(x.getId(), p1);
        for (Candidat x : List.of(e, f, g)) pouleDe.put(x.getId(), p2);
        lenient().when(affectationPouleRepository.findByCandidatIdAndPoulePhaseId(any(), any())).thenAnswer(inv -> {
            UUID cid = inv.getArgument(0);
            Poule p = pouleDe.get(cid);
            if (p == null) return Optional.empty();
            Candidat cand = candidats.stream().filter(k -> k.getId().equals(cid)).findFirst().orElseThrow();
            return Optional.of(AffectationPoule.builder().id(UUID.randomUUID()).candidat(cand).poule(p).build());
        });

        List<ResultatPhase> res = classementService.calculerClassementPhase(el.getId());

        Map<String, StatutQualification> expected = new HashMap<>();
        expected.put("A", StatutQualification.QUALIFIE);
        expected.put("B", StatutQualification.QUALIFIE);
        expected.put("C", StatutQualification.ELIMINE);
        expected.put("E", StatutQualification.QUALIFIE);
        expected.put("F", StatutQualification.QUALIFIE);
        expected.put("G", StatutQualification.ELIMINE);
        expected.put("X", StatutQualification.ELIMINE);
        assertThat(statutsParCode(res)).containsExactlyInAnyOrderEntriesOf(expected);
        // Profile switch for poules is deferred to appliquerEliminationsStatutProfil
        verify(candidatRepository, never()).saveAll(anyIterable());
        assertThat(sansPoule.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
    }

    // ---------------------------------------------------------------- appliquerEliminationsStatutProfil

    @Test
    @DisplayName("7. appliquerEliminationsStatutProfil sur soiree de demi-finale (duos, aucune poule) : ELIMINE -> profil ELIMINE, QUALIFIE reste ACTIF")
    void appliquerEliminationsStatutProfil_soireeDuos() {
        Phase df = phase(NomPhase.DEMI_FINALE);
        SoireeEvent s = soiree(df, StatutSoiree.TERMINEE);
        Candidat a = candidat("A", 0), b = candidat("B", 0), c = candidat("C", 0), d = candidat("D", 0);
        duo(df, s, a, c);
        duo(df, s, b, d);
        resultatExistant(a, df, StatutQualification.QUALIFIE);
        resultatExistant(b, df, StatutQualification.QUALIFIE);
        resultatExistant(c, df, StatutQualification.ELIMINE);
        resultatExistant(d, df, StatutQualification.ELIMINE);
        lenient().when(affectationPouleRepository.findByPouleSoireeId(s.getId())).thenReturn(List.of());

        classementService.appliquerEliminationsStatutProfil(s.getId());

        assertThat(profilsSauvegardes()).extracting(Candidat::getCodeCandidat).containsExactlyInAnyOrder("C", "D");
        assertThat(a.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
        assertThat(b.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
        assertThat(c.getStatutProfil()).isEqualTo(StatutProfilCandidat.ELIMINE);
        assertThat(d.getStatutProfil()).isEqualTo(StatutProfilCandidat.ELIMINE);
    }

    @Test
    @DisplayName("7b. appliquerEliminationsStatutProfil : soiree en poules toujours traitee (non-regression)")
    void appliquerEliminationsStatutProfil_soireePoules() {
        Phase el = phase(NomPhase.ELIMINATOIRES);
        SoireeEvent s = soiree(el, StatutSoiree.TERMINEE);
        Poule p = Poule.builder().id(UUID.randomUUID()).phase(el).soiree(s).build();
        Candidat a = candidat("A", 0), c = candidat("C", 0);
        resultatExistant(a, el, StatutQualification.QUALIFIE);
        resultatExistant(c, el, StatutQualification.ELIMINE);
        Function<Candidat, AffectationPoule> aff = k -> AffectationPoule.builder().candidat(k).poule(p).build();
        lenient().when(affectationPouleRepository.findByPouleSoireeId(s.getId())).thenReturn(List.of(aff.apply(a), aff.apply(c)));

        classementService.appliquerEliminationsStatutProfil(s.getId());

        assertThat(profilsSauvegardes()).extracting(Candidat::getCodeCandidat).containsExactly("C");
        assertThat(a.getStatutProfil()).isEqualTo(StatutProfilCandidat.ACTIF);
    }
}
