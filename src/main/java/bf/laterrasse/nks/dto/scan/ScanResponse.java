package bf.laterrasse.nks.dto.scan;

import java.time.Instant;

public record ScanResponse(String resultat, String nomSpectateur, Integer nbPlaces, Instant timestampPremierScan) {
}
