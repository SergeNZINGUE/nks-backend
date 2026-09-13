package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.votesurplace.DroitVoteResponse;
import bf.laterrasse.nks.dto.votesurplace.VoterSurPlaceRequest;
import bf.laterrasse.nks.service.VoteSurPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Vote public sur place — aucune authentification requise, comme le reste du parcours
 * billetterie/réservation public (mêmes conventions que BilletterieController) : seule la
 * connaissance du qrUuid du billet donne accès, exactement le même modèle de confiance que
 * le QR billet lui-même. Voir VoteSurPlaceService pour le détail anti-fraude.
 */
@RestController
@RequestMapping("/vote-sur-place")
@RequiredArgsConstructor
public class VoteSurPlaceController {

    private final VoteSurPlaceService voteSurPlaceService;

    @GetMapping("/{soireeId}/{qrUuid}")
    @Transactional(readOnly = true)
    public ResponseEntity<DroitVoteResponse> consulter(@PathVariable UUID soireeId, @PathVariable UUID qrUuid) {
        return ResponseEntity.ok(voteSurPlaceService.consulterDroit(qrUuid, soireeId));
    }

    @PostMapping("/{soireeId}/{qrUuid}/voter")
    public ResponseEntity<DroitVoteResponse> voter(@PathVariable UUID soireeId, @PathVariable UUID qrUuid,
                                                    @Valid @RequestBody VoterSurPlaceRequest request) {
        return ResponseEntity.ok(voteSurPlaceService.voter(qrUuid, soireeId, request.candidatId(),
                request.telephoneVotant(), request.positionLatitude(), request.positionLongitude(),
                request.positionPrecisionM()));
    }
}
