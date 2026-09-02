package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.*;
import bf.laterrasse.nks.dto.jury.NoteInput;
import bf.laterrasse.nks.dto.jury.SaisirNotesRequest;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** WF-06 — Notation jury, grille à 6 critères (§3.4), verrouillage après clôture soirée. */
@Service
@RequiredArgsConstructor
public class JuryService {

    private final JuryRepository juryRepository;
    private final CandidatRepository candidatRepository;
    private final SoireeEventRepository soireeEventRepository;
    private final CritereNotationRepository critereNotationRepository;
    private final NoteJuryRepository noteJuryRepository;
    private final ClassementService classementService;

    @Transactional
    public List<NoteJury> saisirNotes(UUID utilisateurJuryId, SaisirNotesRequest request) {
        Jury jury = juryRepository.findByUtilisateurId(utilisateurJuryId)
                .orElseThrow(() -> new AccesRefuseException("Cet utilisateur n'est pas membre du jury"));
        SoireeEvent soiree = soireeEventRepository.findById(request.soireeId())
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));

        if (!jury.getSoirees().contains(soiree)) {
            throw new AccesRefuseException("Cette soirée n'est pas affectée à ce juré");
        }
        if (noteJuryRepository.existsBySoireeIdAndVerrouilleTrue(soiree.getId())) {
            throw new ConflitEtatException("Les notes de cette soirée sont clôturées et verrouillées");
        }

        Candidat candidat = candidatRepository.findById(request.candidatId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));

        return request.notes().stream()
                .map(input -> enregistrerNote(jury, candidat, soiree, input))
                .toList();
    }

    private NoteJury enregistrerNote(Jury jury, Candidat candidat, SoireeEvent soiree, NoteInput input) {
        CritereNotation critere = critereNotationRepository.findById(input.critereId())
                .orElseThrow(() -> new ResourceNotFoundException("Critère de notation introuvable"));

        if (input.valeur().compareTo(critere.getNoteMin()) < 0 || input.valeur().compareTo(critere.getNoteMax()) > 0) {
            throw new ValidationMetierException("Note hors plage pour le critère '" + critere.getNom()
                    + "' (attendu entre " + critere.getNoteMin() + " et " + critere.getNoteMax() + ")");
        }

        NoteJury note = noteJuryRepository
                .findByJuryIdAndCandidatIdAndSoireeIdAndCritereId(jury.getId(), candidat.getId(), soiree.getId(), critere.getId())
                .orElse(NoteJury.builder().jury(jury).candidat(candidat).soiree(soiree).critere(critere).build());

        if (note.isVerrouille()) {
            throw new ConflitEtatException("Cette note est déjà verrouillée");
        }
        note.setValeur(input.valeur());
        note.setDateModification(Instant.now());
        return noteJuryRepository.save(note);
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "RESULTATS_SOIREE_PUBLIES", entite = "SoireeEvent")
    public void publierResultats(UUID soireeId) {
        SoireeEvent soiree = soireeEventRepository.findById(soireeId)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));
        soiree.setResultatsPublies(true);
        soireeEventRepository.save(soiree);
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "NOTATION_SOIREE_DEVERROUILLEE", entite = "SoireeEvent")
    public void deverrouillerSoiree(UUID soireeId) {
        soireeEventRepository.findById(soireeId)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));
        List<NoteJury> notes = noteJuryRepository.findBySoireeId(soireeId);
        Instant now = Instant.now();
        notes.forEach(n -> {
            n.setVerrouille(false);
            n.setDateModification(now);
        });
        noteJuryRepository.saveAll(notes);
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "NOTATION_SOIREE_CLOTUREE", entite = "SoireeEvent")
    public void cloturerSoiree(UUID soireeId) {
        SoireeEvent soiree = soireeEventRepository.findById(soireeId)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));

        List<NoteJury> notes = noteJuryRepository.findBySoireeId(soireeId);
        Instant now = Instant.now();
        notes.forEach(n -> {
            n.setVerrouille(true);
            n.setDateModification(now);
        });
        noteJuryRepository.saveAll(notes);

        classementService.calculerClassementPhase(soiree.getPhase().getId());
    }
}
