package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.admin.AffecterSoireesJuryRequest;
import bf.laterrasse.nks.dto.admin.AuditLogResponse;
import bf.laterrasse.nks.dto.admin.CommunicationRequest;
import bf.laterrasse.nks.dto.admin.CreerCritereNotationRequest;
import bf.laterrasse.nks.dto.admin.CreerJuryRequest;
import bf.laterrasse.nks.dto.admin.CreerUtilisateurAdminRequest;
import bf.laterrasse.nks.dto.admin.CritereNotationAdminResponse;
import bf.laterrasse.nks.dto.admin.DashboardOrganisateurResponse;
import bf.laterrasse.nks.dto.admin.DashboardResponse;
import bf.laterrasse.nks.dto.admin.MettreAJourCritereNotationRequest;
import bf.laterrasse.nks.dto.admin.ReinitialiserMotDePasseCandidatResponse;
import bf.laterrasse.nks.dto.admin.UtilisateurAdminResponse;
import bf.laterrasse.nks.dto.jury.JuryResponse;
import bf.laterrasse.nks.dto.titre.CreerTitreImposeRequest;
import bf.laterrasse.nks.dto.titre.StatutChoixTitreCandidatResponse;
import bf.laterrasse.nks.dto.titre.TitreImposeResponse;
import bf.laterrasse.nks.repository.AuditLogRepository;
import bf.laterrasse.nks.repository.JuryRepository;
import bf.laterrasse.nks.service.AdminDashboardService;
import bf.laterrasse.nks.service.ChoixTitreService;
import bf.laterrasse.nks.service.CommunicationService;
import bf.laterrasse.nks.service.CritereNotationAdminService;
import bf.laterrasse.nks.service.JuryAdminService;
import bf.laterrasse.nks.service.RapportService;
import bf.laterrasse.nks.service.TitreImposeAdminService;
import bf.laterrasse.nks.service.UtilisateurAdminService;
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

/**
 * §13.15 — US-34/35, gestion jury, exports.
 * Spring Security 6 évalue class-level ET method-level @PreAuthorize simultanément :
 * la class-level ouvre à ADMIN/SUPER_ADMIN/ORGANISATEUR ; chaque endpoint sensible
 * (jury, critères, rapports, audit, réinitialisation compte admin) redescend explicitement
 * à ADMIN/SUPER_ADMIN via sa propre annotation.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminDashboardService dashboardService;
    private final CommunicationService communicationService;
    private final JuryAdminService juryAdminService;
    private final JuryRepository juryRepository;
    private final RapportService rapportService;
    private final AuditLogRepository auditLogRepository;
    private final UtilisateurAdminService utilisateurAdminService;
    private final CritereNotationAdminService critereNotationAdminService;
    private final TitreImposeAdminService titreImposeAdminService;
    private final ChoixTitreService choixTitreService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(dashboardService.construire());
    }

    @GetMapping("/dashboard/organisateur")
    public ResponseEntity<DashboardOrganisateurResponse> dashboardOrganisateur() {
        return ResponseEntity.ok(dashboardService.construirePourOrganisateur());
    }

    @PostMapping("/communication/envoyer")
    public ResponseEntity<Map<String, Object>> envoyerCommunication(@Valid @RequestBody CommunicationRequest request) {
        return ResponseEntity.ok(communicationService.envoyerGroupe(request));
    }

    @GetMapping("/jury")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<JuryResponse>> jury(@RequestParam UUID editionId) {
        List<JuryResponse> result = juryRepository.findByEditionId(editionId).stream()
                .map(JuryResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jury")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<JuryResponse> creerJury(@Valid @RequestBody CreerJuryRequest request) {
        return ResponseEntity.status(201).body(JuryResponse.from(juryAdminService.creer(request)));
    }

    @DeleteMapping("/jury/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> desactiverJury(@PathVariable UUID id) {
        juryAdminService.desactiver(id);
        return ResponseEntity.noContent().build();
    }

    /** Remplace intégralement l'ensemble des soirées affectées à ce juré. */
    @PutMapping("/jury/{id}/soirees")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<JuryResponse> affecterSoireesJury(@PathVariable UUID id,
                                                             @Valid @RequestBody AffecterSoireesJuryRequest request) {
        return ResponseEntity.ok(JuryResponse.from(juryAdminService.affecterSoirees(id, request.soireeIds())));
    }

    @GetMapping("/criteres-notation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CritereNotationAdminResponse>> criteresNotation(@RequestParam UUID editionId) {
        return ResponseEntity.ok(
                critereNotationAdminService.lister(editionId).stream()
                        .map(CritereNotationAdminResponse::from)
                        .toList());
    }

    @PostMapping("/criteres-notation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<CritereNotationAdminResponse> creerCritereNotation(@Valid @RequestBody CreerCritereNotationRequest request) {
        return ResponseEntity.status(201).body(
                CritereNotationAdminResponse.from(critereNotationAdminService.creer(request)));
    }

    @PutMapping("/criteres-notation/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<CritereNotationAdminResponse> mettreAJourCritereNotation(
            @PathVariable UUID id, @Valid @RequestBody MettreAJourCritereNotationRequest request) {
        return ResponseEntity.ok(
                CritereNotationAdminResponse.from(critereNotationAdminService.mettreAJour(id, request)));
    }

    /** Titres imposés par le CO, publiés par phase (§ échange du 13/09/2026). Ouvert à ORGANISATEUR (contenu, pas financier). */
    @GetMapping("/phases/{phaseId}/titres-imposes")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TitreImposeResponse>> titresImposes(@PathVariable UUID phaseId) {
        return ResponseEntity.ok(
                titreImposeAdminService.lister(phaseId).stream().map(TitreImposeResponse::from).toList());
    }

    @PostMapping("/titres-imposes")
    @Transactional
    public ResponseEntity<TitreImposeResponse> creerTitreImpose(@Valid @RequestBody CreerTitreImposeRequest request) {
        return ResponseEntity.status(201).body(TitreImposeResponse.from(titreImposeAdminService.creer(request)));
    }

    @DeleteMapping("/titres-imposes/{id}")
    @Transactional
    public ResponseEntity<Void> supprimerTitreImpose(@PathVariable UUID id) {
        titreImposeAdminService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /** Rapport : pour chaque candidat affecté à une soirée de cette phase, a-t-il choisi son titre ? */
    @GetMapping("/phases/{phaseId}/choix-titres")
    @Transactional(readOnly = true)
    public ResponseEntity<List<StatutChoixTitreCandidatResponse>> statutChoixTitres(@PathVariable UUID phaseId) {
        return ResponseEntity.ok(choixTitreService.statutPhase(phaseId));
    }

    @GetMapping("/rapports/votes/export-csv")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportVotesCsv(@RequestParam UUID phaseId) {
        byte[] csv = rapportService.exporterVotesPhase(phaseId);
        return csvResponse(csv, "votes-phase-" + phaseId + ".csv");
    }

    @GetMapping("/billetterie/soiree/{id}/export-csv")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> exportTicketsCsv(@PathVariable UUID id) {
        byte[] csv = rapportService.exporterTicketsSoiree(id);
        return csvResponse(csv, "tickets-soiree-" + id + ".csv");
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<AuditLogResponse>> auditLogs(Pageable pageable) {
        Page<AuditLogResponse> result = auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                .map(AuditLogResponse::from);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/utilisateurs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UtilisateurAdminResponse> creerUtilisateur(@Valid @RequestBody CreerUtilisateurAdminRequest request) {
        return ResponseEntity.status(201).body(utilisateurAdminService.creer(request));
    }

    @PostMapping("/utilisateurs/{id}/reinitialiser-mot-de-passe")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> reinitialiserMotDePasse(@PathVariable UUID id) {
        utilisateurAdminService.reinitialiserMotDePasse(id);
        return ResponseEntity.noContent().build();
    }

    /** Réinitialise le mot de passe d'un candidat et retourne le nouveau en clair pour que l'organisateur puisse le transmettre. */
    @PostMapping("/candidats/{id}/reinitialiser-mot-de-passe")
    public ResponseEntity<ReinitialiserMotDePasseCandidatResponse> reinitialiserMotDePasseCandidat(@PathVariable UUID id) {
        return ResponseEntity.ok(utilisateurAdminService.reinitialiserMotDePasseCandidat(id));
    }

    @GetMapping("/utilisateurs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UtilisateurAdminResponse>> listerUtilisateurs() {
        return ResponseEntity.ok(utilisateurAdminService.listerAdmins());
    }

    private ResponseEntity<byte[]> csvResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(content);
    }
}
