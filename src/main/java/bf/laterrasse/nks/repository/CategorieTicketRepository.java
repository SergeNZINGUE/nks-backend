package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.CategorieTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategorieTicketRepository extends JpaRepository<CategorieTicket, UUID> {

    List<CategorieTicket> findBySoireeId(UUID soireeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CategorieTicket c WHERE c.id = :id")
    Optional<CategorieTicket> findByIdForUpdate(UUID id);
}
