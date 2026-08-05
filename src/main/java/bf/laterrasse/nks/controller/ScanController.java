package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.scan.ScanRequest;
import bf.laterrasse.nks.dto.scan.ScanResponse;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.ScanService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** §13.12 — WF-11. */
@RestController
@RequiredArgsConstructor
public class ScanController {

    private final ScanService scanService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/scan")
    @PreAuthorize("hasRole('AGENT_ACCUEIL')")
    public ResponseEntity<ScanResponse> scanner(@Valid @RequestBody ScanRequest request, HttpServletRequest httpRequest) {
        var agent = currentUserProvider.getCurrentUser();
        ScanResponse response = scanService.scanner(request.qrUuid(), request.soireeId(), agent,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/scan/soiree/{id}/compteur")
    @PreAuthorize("hasAnyRole('AGENT_ACCUEIL','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Map<String, Long>> compteur(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("entrees", scanService.compteurEntrees(id)));
    }
}
