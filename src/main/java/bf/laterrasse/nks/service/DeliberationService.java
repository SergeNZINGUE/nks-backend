package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.CritereNotation;
import bf.laterrasse.nks.domain.Duo;
import bf.laterrasse.nks.domain.Jury;
import bf.laterrasse.nks.domain.NoteJury;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.enums.Enums.TypeVote;
import bf.laterrasse.nks.dto.admin.CritereGrilleResponse;
import bf.laterrasse.nks.dto.admin.GrilleDeliberationResponse;
import bf.laterrasse.nks.dto.admin.LigneDeliberationResponse;
import bf.laterrasse.nks.dto.admin.NoteDetailResponse;
import bf.laterrasse.nks.dto.admin.NoteParJuryResponse;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.CritereNotationRepository;
import bf.laterrasse.nks.repository.DuoRepository;
import bf.laterrasse.nks.repository.NoteJuryRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Grille récapitulative d'une soirée pour la délibération finale du jury : notes détaillées
 * par juré et par critère (scopées à la soirée), le vote public sur place (scopé à CETTE
 * soirée uniquement, pas cumulatif sur la phase), et les votes en ligne payants + sociaux
 * (cumulatifs sur toute la phase — cf. LigneDeliberationResponse). Réutilise les mêmes
 * formules de pondération que ClassementService (WF-07) sans persister de résultat : c'est un
 * aperçu en lecture seule, calculable à tout moment, y compris avant que l'admin ne lance le
 * calcul officiel du classement.
 */
@Service
@RequiredArgsConstructor
public class DeliberationService {

    private static final BigDecimal POIDS_LIKE = new BigDecimal("0.25");
    private static final BigDecimal POIDS_COMMENTAIRE = new BigDecimal("0.75");
    private static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final SoireeEventRepository soireeEventRepository;
    private final AffectationPouleRepository affectationPouleRepository;
    private final DuoRepository duoRepository;
    private final NoteJuryRepository noteJuryRepository;
    private final CritereNotationRepository critereNotationRepository;
    private final VoteRepository voteRepository;
    private final VoteService voteService;

    @Transactional(readOnly = true)
    public GrilleDeliberationResponse construire(UUID soireeId) {
        SoireeEvent soiree = soireeEventRepository.findById(soireeId)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));
        Phase phase = soiree.getPhase();

        List<AffectationPoule> viaPoule = affectationPouleRepository.findByPouleSoireeId(soireeId);
        List<Duo> viaDuo = duoRepository.findBySoireeId(soireeId);
        Set<Candidat> candidats = Stream.concat(
                viaPoule.stream().map(AffectationPoule::getCandidat),
                viaDuo.stream().flatMap(d -> Stream.of(d.getCandidat1(), d.getCandidat2()))
        ).collect(Collectors.toCollection(LinkedHashSet::new));

        List<CritereNotation> criteres = critereNotationRepository
                .findByEditionIdAndActifTrueOrderByOrdreAsc(phase.getEdition().getId());

        long totalVoixPayantes = voteService.totalVotesPayantsConfirmes(phase.getId());
        // Vote public sur place : total DE CETTE SOIRÉE uniquement (pas cumulatif sur la phase) —
        // somme des voix des candidats qui se sont produits ce soir-là, cf. ClassementService.
        long totalVoixSurPlaceSoiree = candidats.stream()
                .mapToLong(c -> voteService.votesSurPlace(c.getId(), phase.getId()))
                .sum();
        List<NoteJury> notesSoiree = noteJuryRepository.findBySoireeId(soireeId);

        List<LigneDeliberationResponse> lignes = candidats.stream()
                .map(c -> construireLigne(c, phase, notesSoiree, totalVoixPayantes, totalVoixSurPlaceSoiree))
                .sorted(Comparator.comparing(LigneDeliberationResponse::totalGeneral).reversed())
                .toList();

        boolean notationCloturee = noteJuryRepository.existsBySoireeIdAndVerrouilleTrue(soireeId);

        return new GrilleDeliberationResponse(
                soiree.getId(), soiree.getNom(), soiree.getDateHeure(),
                phase.getId(), phase.getNom().name(), notationCloturee,
                criteres.stream().map(CritereGrilleResponse::from).toList(),
                lignes);
    }

    private LigneDeliberationResponse construireLigne(Candidat candidat, Phase phase, List<NoteJury> notesSoiree,
                                                        long totalVoixPayantes, long totalVoixSurPlace) {
        List<NoteJury> notesCandidat = notesSoiree.stream()
                .filter(n -> n.getCandidat().getId().equals(candidat.getId()))
                .toList();

        Map<UUID, List<NoteJury>> notesParJuryId = notesCandidat.stream()
                .collect(Collectors.groupingBy(n -> n.getJury().getId()));

        List<NoteParJuryResponse> notesParJury = notesParJuryId.values().stream()
                .map(this::construireNotesJury)
                .sorted(Comparator.comparing(NoteParJuryResponse::juryNomComplet))
                .toList();

        BigDecimal totalJuryMoyen = BigDecimal.ZERO;
        BigDecimal pointsJury = BigDecimal.ZERO;
        if (!notesParJury.isEmpty()) {
            BigDecimal sommeTotaux = notesParJury.stream()
                    .map(NoteParJuryResponse::totalJury).reduce(BigDecimal.ZERO, BigDecimal::add);
            totalJuryMoyen = sommeTotaux.divide(BigDecimal.valueOf(notesParJury.size()), 2, RoundingMode.HALF_UP);
            pointsJury = totalJuryMoyen.divide(CENT, 10, RoundingMode.HALF_UP)
                    .multiply(phase.getPointsMaxJury()).setScale(4, RoundingMode.HALF_UP);
        }

        long votesPayants = voteService.votesPayantsConfirmes(candidat.getId(), phase.getId());
        long likes = voteRepository.sommeVoixParCandidatEtTypes(candidat.getId(), phase.getId(), List.of(TypeVote.SOCIAL_LIKE));
        long commentaires = voteRepository.sommeVoixParCandidatEtTypes(candidat.getId(), phase.getId(), List.of(TypeVote.SOCIAL_COMMENTAIRE));
        BigDecimal pointsPayants = ratio(votesPayants, totalVoixPayantes, phase.getPointsMaxVotesEnLigne());
        BigDecimal pointsSociaux = POIDS_LIKE.multiply(BigDecimal.valueOf(likes))
                .add(POIDS_COMMENTAIRE.multiply(BigDecimal.valueOf(commentaires)));
        BigDecimal pointsVotesEnLigne = pointsPayants.add(pointsSociaux)
                .min(phase.getPointsMaxVotesEnLigne()).setScale(4, RoundingMode.HALF_UP);

        long votesPublic = voteService.votesSurPlace(candidat.getId(), phase.getId());
        BigDecimal pointsPublic = ratio(votesPublic, totalVoixSurPlace, phase.getPointsMaxPublic());

        BigDecimal totalGeneral = pointsJury.add(pointsVotesEnLigne).add(pointsPublic);

        return new LigneDeliberationResponse(
                candidat.getId(), candidat.getCodeCandidat(),
                candidat.getUtilisateur().getPrenom(), candidat.getUtilisateur().getNom(),
                notesParJury, totalJuryMoyen, pointsJury,
                votesPayants, likes, commentaires, pointsVotesEnLigne,
                votesPublic, pointsPublic,
                totalGeneral);
    }

    private NoteParJuryResponse construireNotesJury(List<NoteJury> notes) {
        Jury jury = notes.get(0).getJury();

        // Nombre de passages distincts saisis par ce juré pour ce candidat.
        long nbPassages = notes.stream().map(NoteJury::getNumeroPassage).distinct().count();
        if (nbPassages == 0) nbPassages = 1;
        final long diviseur = nbPassages;

        // Une ligne par critère : valeur = moyenne des passages (cohérent avec ClassementService).
        List<NoteDetailResponse> details = notes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.getCritere().getId(),
                        Collectors.toList()))
                .entrySet().stream()
                .sorted(Comparator.comparingInt(e ->
                        e.getValue().get(0).getCritere().getOrdre()))
                .map(e -> {
                    NoteJury ref = e.getValue().get(0);
                    BigDecimal somme = e.getValue().stream()
                            .map(NoteJury::getValeur).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal moyenne = somme.divide(BigDecimal.valueOf(diviseur), 2, RoundingMode.HALF_UP);
                    return new NoteDetailResponse(ref.getCritere().getId(), ref.getCritere().getNom(), moyenne);
                })
                .toList();

        // totalJury = moyenne des totaux par passage (même formule que ClassementService.calculerPointsJury).
        BigDecimal sommeTotale = notes.stream().map(NoteJury::getValeur).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = sommeTotale.divide(BigDecimal.valueOf(diviseur), 2, RoundingMode.HALF_UP);

        return new NoteParJuryResponse(jury.getId(), jury.getPrenom() + " " + jury.getNom(), details, total);
    }

    /** Même formule que ClassementService.calculerPointsRatio — dupliquée pour ne pas coupler ce rapport en lecture seule au pipeline de calcul officiel du classement. */
    private BigDecimal ratio(long voixCandidat, long totalVoix, BigDecimal pointsMax) {
        if (totalVoix <= 0 || voixCandidat <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(voixCandidat)
                .divide(BigDecimal.valueOf(totalVoix), 10, RoundingMode.HALF_UP)
                .multiply(pointsMax)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
