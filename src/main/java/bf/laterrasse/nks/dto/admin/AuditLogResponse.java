package bf.laterrasse.nks.dto.admin;

import bf.laterrasse.nks.domain.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        UUID utilisateurId,
        String action,
        String entiteConcernee,
        UUID entiteId,
        String ipSource,
        Instant timestamp
) {
    public static AuditLogResponse from(AuditLog a) {
        return new AuditLogResponse(
                a.getId(),
                a.getUtilisateur() != null ? a.getUtilisateur().getId() : null,
                a.getAction(),
                a.getEntiteConcernee(),
                a.getEntiteId(),
                a.getIpSource(),
                a.getTimestamp());
    }
}
