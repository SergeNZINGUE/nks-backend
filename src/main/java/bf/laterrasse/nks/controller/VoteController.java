package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.vote.InitierVoteRequest;
import bf.laterrasse.nks.dto.vote.InitierVoteResponse;
import bf.laterrasse.nks.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** §13.6 — WF-04. */
@RestController
@RequestMapping("/votes")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @PostMapping("/initier")
    public ResponseEntity<InitierVoteResponse> initier(@Valid @RequestBody InitierVoteRequest request) {
        return ResponseEntity.ok(voteService.initierVotePayant(request));
    }

    @GetMapping("/candidat/{candidatId}")
    public ResponseEntity<Map<String, Long>> compteur(@PathVariable UUID candidatId, @RequestParam UUID phaseId) {
        long payants = voteService.votesPayantsConfirmes(candidatId, phaseId);
        long sociaux = voteService.votesSociaux(candidatId, phaseId);
        long surPlace = voteService.votesSurPlace(candidatId, phaseId);
        return ResponseEntity.ok(Map.of(
                "votesPayants", payants,
                "votesSociaux", sociaux,
                "votesSurPlace", surPlace,
                "total", payants + sociaux + surPlace));
    }
}
