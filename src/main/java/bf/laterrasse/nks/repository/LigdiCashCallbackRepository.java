package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.LigdiCashCallback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LigdiCashCallbackRepository extends JpaRepository<LigdiCashCallback, Long> {
    boolean existsByToken(String token);
}
