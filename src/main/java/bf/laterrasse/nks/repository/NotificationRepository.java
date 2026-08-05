package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Notification;
import bf.laterrasse.nks.domain.enums.Enums.StatutEnvoiNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUtilisateurIdOrderByDateCreationDesc(UUID utilisateurId, Pageable pageable);

    List<Notification> findByStatutEnvoiAndNbTentativesLessThan(StatutEnvoiNotification statut, short maxTentatives);

    long countByUtilisateurIdAndLuFalse(UUID utilisateurId);
}
