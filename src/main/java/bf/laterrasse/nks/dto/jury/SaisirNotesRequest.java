package bf.laterrasse.nks.dto.jury;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SaisirNotesRequest(
        @NotNull UUID candidatId,
        @NotNull UUID soireeId,
        @NotEmpty @Valid List<NoteInput> notes
) {
}
