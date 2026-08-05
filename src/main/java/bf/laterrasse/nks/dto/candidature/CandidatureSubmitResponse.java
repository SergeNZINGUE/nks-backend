package bf.laterrasse.nks.dto.candidature;

import java.util.UUID;

public record CandidatureSubmitResponse(UUID id, String codeCandidat, String statut) {
}
