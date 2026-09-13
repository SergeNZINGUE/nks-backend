package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.TitreImpose;
import bf.laterrasse.nks.dto.titre.CreerTitreImposeRequest;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.PhaseRepository;
import bf.laterrasse.nks.repository.TitreImposeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Gestion, par le CO, de la liste des titres imposés d'une phase (§ échange du 13/09/2026). */
@Service
@RequiredArgsConstructor
public class TitreImposeAdminService {

    private final TitreImposeRepository titreImposeRepository;
    private final PhaseRepository phaseRepository;

    @Transactional(readOnly = true)
    public List<TitreImpose> lister(UUID phaseId) {
        return titreImposeRepository.findByPhaseIdOrderByOrdreAsc(phaseId);
    }

    @Transactional
    public TitreImpose creer(CreerTitreImposeRequest request) {
        Phase phase = phaseRepository.findById(request.phaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));

        TitreImpose titre = TitreImpose.builder()
                .phase(phase)
                .titre(request.titre())
                .ordre(request.ordre() != null ? request.ordre() : 0)
                .build();
        return titreImposeRepository.save(titre);
    }

    @Transactional
    public void supprimer(UUID id) {
        if (!titreImposeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Titre imposé introuvable");
        }
        titreImposeRepository.deleteById(id);
    }
}
