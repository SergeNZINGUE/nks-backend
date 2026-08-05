package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.Vote;
import bf.laterrasse.nks.repository.TicketRepository;
import bf.laterrasse.nks.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * US-39/US-40 — Exports CSV. ⚠️ Les exports PDF (rapport financier §13.15, palmarès §13.14)
 * décrits dans le rapport nécessitent une librairie de génération PDF non encore intégrée
 * au projet (ex. OpenPDF/iText) — voir README §Travail restant. Le CSV ci-dessous couvre
 * le même besoin fonctionnel en attendant.
 */
@Service
@RequiredArgsConstructor
public class RapportService {

    private final TicketRepository ticketRepository;
    private final VoteRepository voteRepository;

    public byte[] exporterTicketsSoiree(UUID soireeId) {
        var out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("nom;code_ticket;categorie;statut");
            java.util.List<Ticket> tickets = ticketRepository.findAll().stream()
                    .filter(t -> t.getSoiree().getId().equals(soireeId))
                    .toList();
            for (Ticket t : tickets) {
                writer.printf("%s;%s;%s;%s%n",
                        echapper(t.getNomSpectateur()), t.getId(), t.getCategorie().getNom(), t.getStatut());
            }
        }
        return out.toByteArray();
    }

    public byte[] exporterVotesPhase(UUID phaseId) {
        var out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            writer.println("candidat_code;type_vote;nombre_voix;date_vote");
            java.util.List<Vote> votes = voteRepository.findByPhaseId(phaseId);
            for (Vote v : votes) {
                writer.printf("%s;%s;%d;%s%n",
                        v.getCandidat().getCodeCandidat(), v.getTypeVote(), v.getNombreVoix(), v.getDateVote());
            }
        }
        return out.toByteArray();
    }

    private String echapper(String value) {
        return value == null ? "" : value.replace(";", ",");
    }
}
