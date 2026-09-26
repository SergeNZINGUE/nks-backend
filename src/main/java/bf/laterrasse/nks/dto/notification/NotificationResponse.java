package bf.laterrasse.nks.dto.notification;

import bf.laterrasse.nks.domain.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String corpsMessage,
        boolean lu,
        Instant dateCreation
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getTypeNotification().name(),
                n.getCorpsMessage(),
                n.isLu(),
                n.getDateCreation());
    }
}
