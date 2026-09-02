package bf.laterrasse.nks.dto.vote;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InitierVoteRequest(
        @NotNull UUID candidatId,
        @NotNull UUID phaseId,
        @Min(1) int nbVotes,
        String telephone
) {
}
