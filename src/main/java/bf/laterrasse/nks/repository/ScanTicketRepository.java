package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.ScanTicket;
import bf.laterrasse.nks.domain.enums.Enums.ResultatScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanTicketRepository extends JpaRepository<ScanTicket, UUID> {
    List<ScanTicket> findBySoireeId(UUID soireeId);
    Optional<ScanTicket> findFirstByQrcodeIdAndSoireeIdAndResultat(UUID qrcodeId, UUID soireeId, ResultatScan resultat);
    long countBySoireeIdAndResultat(UUID soireeId, ResultatScan resultat);
}
