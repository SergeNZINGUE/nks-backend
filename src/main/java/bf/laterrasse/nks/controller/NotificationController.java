package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.enums.Enums.CanalNotification;
import bf.laterrasse.nks.dto.notification.NotificationResponse;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.NotificationRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Cloche in-app — premier consommateur réel du canal {@link CanalNotification#IN_APP}
 * (défini dans l'enum depuis le début, jamais branché avant "Moments de l'événement").
 * Générique et réutilisable : n'importe quelle fonctionnalité future peut émettre des
 * notifications lues ici sans rien ajouter côté contrôleur.
 */
@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/notifications/mes-notifications")
    public ResponseEntity<List<NotificationResponse>> mesNotifications(
            @RequestParam(defaultValue = "20") int limite) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        List<NotificationResponse> notifications = notificationRepository
                .findByUtilisateurIdAndCanalOrderByDateCreationDesc(
                        utilisateurId, CanalNotification.IN_APP, PageRequest.of(0, limite))
                .map(NotificationResponse::from)
                .getContent();
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/notifications/non-lues/nombre")
    public ResponseEntity<Long> nombreNonLues() {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(
                notificationRepository.countByUtilisateurIdAndCanalAndLuFalse(utilisateurId, CanalNotification.IN_APP));
    }

    @PutMapping("/notifications/{id}/lu")
    public ResponseEntity<Void> marquerLu(@PathVariable UUID id) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification introuvable"));
        if (!notification.getUtilisateur().getId().equals(utilisateurId)) {
            throw new AccesRefuseException("Cette notification ne t'appartient pas");
        }
        notification.setLu(true);
        notificationRepository.save(notification);
        return ResponseEntity.noContent().build();
    }
}
