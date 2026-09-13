package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.CritereNotation;
import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.dto.admin.CreerCritereNotationRequest;
import bf.laterrasse.nks.dto.admin.MettreAJourCritereNotationRequest;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.CritereNotationRepository;
import bf.laterrasse.nks.repository.EditionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Gestion de la grille de critères de notation jury par édition (§3.4 du CdC). Avant ce
 * service, seul un seed SQL manuel (`criteres_notation`) permettait d'en créer — aucun moyen
 * pour l'admin d'ajuster la grille (nouvelle édition, correction d'un barème) sans accès direct
 * à la base.
 */
@Service
@RequiredArgsConstructor
public class CritereNotationAdminService {

    private final CritereNotationRepository critereNotationRepository;
    private final EditionRepository editionRepository;

    @Transactional(readOnly = true)
    public List<CritereNotation> lister(UUID editionId) {
        return critereNotationRepository.findByEditionIdOrderByOrdreAsc(editionId);
    }

    @Transactional
    public CritereNotation creer(CreerCritereNotationRequest request) {
        Edition edition = editionRepository.findById(request.editionId())
                .orElseThrow(() -> new ResourceNotFoundException("Édition introuvable"));

        BigDecimal noteMin = request.noteMin() != null ? request.noteMin() : BigDecimal.ZERO;
        valider(noteMin, request.noteMax());

        CritereNotation critere = CritereNotation.builder()
                .edition(edition)
                .nom(request.nom())
                .noteMin(noteMin)
                .noteMax(request.noteMax())
                .ordre(request.ordre())
                .actif(true)
                .build();
        return critereNotationRepository.save(critere);
    }

    @Transactional
    public CritereNotation mettreAJour(UUID id, MettreAJourCritereNotationRequest request) {
        CritereNotation critere = critereNotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Critère de notation introuvable"));

        BigDecimal noteMin = request.noteMin() != null ? request.noteMin() : BigDecimal.ZERO;
        valider(noteMin, request.noteMax());

        critere.setNom(request.nom());
        critere.setNoteMin(noteMin);
        critere.setNoteMax(request.noteMax());
        critere.setOrdre(request.ordre());
        critere.setActif(request.actif());
        return critereNotationRepository.save(critere);
    }

    private void valider(BigDecimal noteMin, BigDecimal noteMax) {
        if (noteMax.compareTo(noteMin) <= 0) {
            throw new ValidationMetierException("La note max doit être strictement supérieure à la note min");
        }
    }
}
