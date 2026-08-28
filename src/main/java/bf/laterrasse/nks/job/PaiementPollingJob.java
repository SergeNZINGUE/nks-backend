package bf.laterrasse.nks.job;

import bf.laterrasse.nks.repository.PaiementRepository;
import bf.laterrasse.nks.service.PaiementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Polling de secours pour les paiements PENDING sans callback (ex. OTP invalide —
 * LigdiCash n'envoie pas de callback dans ce cas). Interroge confirmInvoice toutes
 * les 2 minutes pour les paiements PENDING de plus de 2 minutes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaiementPollingJob {

    private static final int SEUIL_MINUTES = 2;
    private static final int MAX_TENTATIVES = 10;

    private final PaiementRepository paiementRepository;
    private final PaiementService paiementService;

    @Scheduled(fixedRate = 120_000)
    public void pollPaiementsPending() {
        Instant seuil = Instant.now().minus(SEUIL_MINUTES, ChronoUnit.MINUTES);
        List<UUID> ids = paiementRepository
                .findPendingEligiblesPolling(seuil, MAX_TENTATIVES)
                .stream().map(p -> p.getId()).toList();

        if (ids.isEmpty()) return;

        log.info("PaiementPollingJob : {} paiement(s) PENDING éligible(s)", ids.size());
        for (UUID id : ids) {
            try {
                paiementService.interrogerStatutParPolling(id);
            } catch (Exception e) {
                log.error("Erreur polling paiement {} : {}", id, e.getMessage());
            }
        }
    }
}
