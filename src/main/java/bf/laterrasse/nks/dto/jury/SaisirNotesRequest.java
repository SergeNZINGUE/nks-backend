package bf.laterrasse.nks.dto.jury;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SaisirNotesRequest(
        @NotNull UUID candidatId,
        @NotNull UUID soireeId,
        @NotNull @Min(1) @Max(2) Integer numeroPassage,
        @NotEmpty @Valid List<NoteInput> notes
) {
}
