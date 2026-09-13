package bf.laterrasse.nks.event;

import java.util.UUID;

/** Déclenche un recalcul immédiat du classement d'une phase après confirmation d'un paiement de vote. */
public record ClassementRefreshEvent(UUID phaseId, UUID editionId) {}
