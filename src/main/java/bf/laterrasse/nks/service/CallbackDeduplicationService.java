package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.LigdiCashCallback;
import bf.laterrasse.nks.repository.LigdiCashCallbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Déduplication atomique des callbacks LigdiCash dans une transaction indépendante
 * (REQUIRES_NEW) : si la contrainte UNIQUE (token) est violée, seule cette sous-transaction
 * est invalidée — la transaction appelante reste propre et peut continuer à utiliser JPA.
 *
 * Retourne true si le callback est nouveau, false si c'est un doublon.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CallbackDeduplicationService {

    private final LigdiCashCallbackRepository callbackRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryEnregistrer(String token) {
        try {
            callbackRepository.saveAndFlush(new LigdiCashCallback(token));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.debug("Callback LigdiCash {} déjà enregistré (doublon)", token);
            return false;
        }
    }
}
