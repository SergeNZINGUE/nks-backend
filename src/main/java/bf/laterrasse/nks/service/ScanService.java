package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.QRCodeTicket;
import bf.laterrasse.nks.domain.ScanTicket;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.ResultatScan;
import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import bf.laterrasse.nks.dto.scan.ScanResponse;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.QRCodeTicketRepository;
import bf.laterrasse.nks.repository.ScanTicketRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * WF-11 — Anti-double scan (§14.6) : verrou pessimiste sur la ligne QR code
 * (QRCodeTicketRepository.findByCodeUuidForUpdate) le temps de la vérification +
 * mise à jour, dans la même transaction. Deux scans concurrents du même QR code ne
 * peuvent donc jamais être tous les deux acceptés.
 */
@Service
@RequiredArgsConstructor
public class ScanService {

    private static final ZoneId FUSEAU_OUAGA = ZoneId.of("Africa/Ouagadougou");
    private static final DateTimeFormatter FORMAT_DATE_SCAN =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH:mm", Locale.FRENCH);

    private final QRCodeTicketRepository qrCodeTicketRepository;
    private final TicketRepository ticketRepository;
    private final ScanTicketRepository scanTicketRepository;
    private final SoireeEventRepository soireeEventRepository;

    @Transactional
    public ScanResponse scanner(UUID qrUuid, UUID soireeId, Utilisateur agent, String ipAgent, String deviceInfo) {
        SoireeEvent soiree = soireeEventRepository.findById(soireeId)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));
        var qrOpt = qrCodeTicketRepository.findByCodeUuidForUpdate(qrUuid);

        if (qrOpt.isEmpty()) {
            return new ScanResponse(ResultatScan.INVALIDE.name(), null, null, null,
                    "Ce QR code ne correspond à aucun billet NKS connu.");
        }
        QRCodeTicket qr = qrOpt.get();
        Ticket ticket = qr.getTicket();

        if (!ticket.getSoiree().getId().equals(soireeId)) {
            enregistrerScan(qr, soiree, agent, ResultatScan.INVALIDE, ipAgent, deviceInfo);
            String motif = "Ce billet a été acheté pour la soirée « " + ticket.getSoiree().getNom()
                    + " », pas pour « " + soiree.getNom() + "» (la soirée actuellement active). "
                    + "Vérifie que tu es bien connectée à la bonne soirée.";
            return new ScanResponse(ResultatScan.INVALIDE.name(), null, null, null, motif);
        }

        if (ticket.getStatut() == StatutTicket.UTILISE || !qr.isValide()) {
            var premierScan = scanTicketRepository.findFirstByQrcodeIdAndSoireeIdAndResultat(
                    qr.getId(), soireeId, ResultatScan.VALIDE);
            enregistrerScan(qr, soiree, agent, ResultatScan.DEJA_UTILISE, ipAgent, deviceInfo);
            String motif = "Ce billet a déjà été scanné pour cette soirée"
                    + premierScan.map(s -> " (première fois le "
                            + FORMAT_DATE_SCAN.format(s.getTimestampScan().atZone(FUSEAU_OUAGA)) + ")")
                            .orElse("")
                    + ".";
            return new ScanResponse(ResultatScan.DEJA_UTILISE.name(), ticket.getNomSpectateur(), 1,
                    premierScan.map(ScanTicket::getTimestampScan).orElse(null), motif);
        }

        if (ticket.getStatut() == StatutTicket.ANNULE || ticket.getStatut() == StatutTicket.EXPIRE) {
            String motif = ticket.getStatut() == StatutTicket.ANNULE
                    ? "Ce billet a été annulé — il n'est plus valable."
                    : "Ce billet a expiré : la soirée pour laquelle il avait été acheté est déjà terminée.";
            enregistrerScan(qr, soiree, agent, ResultatScan.INVALIDE, ipAgent, deviceInfo);
            return new ScanResponse(ResultatScan.INVALIDE.name(), null, null, null, motif);
        }

        ticket.setStatut(StatutTicket.UTILISE);
        ticketRepository.save(ticket);
        qr.setValide(false);
        qrCodeTicketRepository.save(qr);

        enregistrerScan(qr, soiree, agent, ResultatScan.VALIDE, ipAgent, deviceInfo);
        return new ScanResponse(ResultatScan.VALIDE.name(), ticket.getNomSpectateur(), 1, null, null);
    }

    private void enregistrerScan(QRCodeTicket qr, SoireeEvent soiree, Utilisateur agent,
                                  ResultatScan resultat, String ip, String device) {
        scanTicketRepository.save(ScanTicket.builder()
                .qrcode(qr)
                .soiree(soiree)
                .agent(agent)
                .resultat(resultat)
                .ipAgent(ip)
                .deviceInfo(device)
                .build());
    }

    public long compteurEntrees(UUID soireeId) {
        return scanTicketRepository.countBySoireeIdAndResultat(soireeId, ResultatScan.VALIDE);
    }
}
