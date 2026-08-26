package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.admin.AuditLogResponse;
import bf.laterrasse.nks.dto.admin.CommunicationRequest;
import bf.laterrasse.nks.dto.admin.CreerJuryRequest;
import bf.laterrasse.nks.dto.admin.DashboardResponse;
import bf.laterrasse.nks.dto.jury.JuryResponse;
import bf.laterrasse.nks.repository.AuditLogRepository;
import bf.laterrasse.nks.repository.JuryRepository;
import bf.laterrasse.nks.service.AdminDashboardService;
import bf.laterrasse.nks.service.CommunicationService;
import bf.laterrasse.nks.service.JuryAdminService;
import bf.laterrasse.nks.service.RapportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** §13.15 — US-34/35, gestion jury, exports. Tout le module est réservé ADMIN/SUPER_ADMIN. */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final CommunicationService communicationService;
    private final JuryAdminService juryAdminService;
    private final JuryRepository juryRepository;
    private final RapportService rapportService;
    private final AuditLogRepository auditLogRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(dashboardService.construire());
    }

    @PostMapping("/communication/envoyer")
    public ResponseEntity<Map<String, Object>> envoyerCommunication(@Valid @RequestBody CommunicationRequest request) {
        return ResponseEntity.ok(communicationService.envoyerGroupe(request));
    }

    @GetMapping("/jury")
    @Transactional(readOnly = true)
    public ResponseEntity<List<JuryResponse>> jury(@RequestParam UUID editionId) {
        List<JuryResponse> result = juryRepository.findByEditionId(editionId).stream()
                .map(JuryResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jury")
    @Transactional
    public ResponseEntity<JuryResponse> creerJury(@Valid @RequestBody CreerJuryRequest request) {
        return ResponseEntity.status(201).body(JuryResponse.from(juryAdminService.creer(request)));
    }

    @DeleteMapping("/jury/{id}")
    public ResponseEntity<Void> desactiverJury(@PathVariable UUID id) {
        juryAdminService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rapports/votes/export-csv")
    public ResponseEntity<byte[]> exportVotesCsv(@RequestParam UUID phaseId) {
        byte[] csv = rapportService.exporterVotesPhase(phaseId);
        return csvResponse(csv, "votes-phase-" + phaseId + ".csv");
    }

    @GetMapping("/billetterie/soiree/{id}/export-csv")
    public ResponseEntity<byte[]> exportTicketsCsv(@PathVariable UUID id) {
        byte[] csv = rapportService.exporterTicketsSoiree(id);
        return csvResponse(csv, "tickets-soiree-" + id + ".csv");
    }

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<AuditLogResponse>> auditLogs(Pageable pageable) {
        Page<AuditLogResponse> result = auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(AuditLogResponse::from);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<byte[]> csvResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content);
    }
}
