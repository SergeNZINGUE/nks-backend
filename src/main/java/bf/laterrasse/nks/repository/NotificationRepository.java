package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.enums.Enums.CanalNotification;
import bf.laterrasse.nks.domain.enums.Enums.StatutEnvoiNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Filtrées par canal (jamais utilisées avant l'ajout du canal IN_APP réel — ces deux
    // méthodes existaient déjà mais sans consommateur, cf. mémoire projet) : sans ça,
    // la cloche in-app remonterait aussi les journaux d'envoi SMS/WhatsApp/e-mail, qui
    // ne sont pas des notifications destinées à être lues dans l'app.
    Page<Notification> findByUtilisateurIdAndCanalOrderByDateCreationDesc(
            UUID utilisateurId, CanalNotification canal, Pageable pageable);

    long countByUtilisateurIdAndCanalAndLuFalse(UUID utilisateurId, CanalNotification canal);

    List<Notification> findByStatutEnvoiAndNbTentativesLessThan(StatutEnvoiNotification statut, short maxTentatives);
}
