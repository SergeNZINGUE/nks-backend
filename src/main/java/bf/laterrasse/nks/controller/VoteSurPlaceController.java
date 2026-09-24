package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.votesurplace.AppareilTokenResponse;
import bf.laterrasse.nks.dto.votesurplace.DroitVoteResponse;
import bf.laterrasse.nks.dto.votesurplace.VoterSurPlaceRequest;
import bf.laterrasse.nks.service.AppareilVoteService;
import bf.laterrasse.nks.service.VoteSurPlaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Vote public sur place — aucune authentification requise, comme le reste du parcours
 * billetterie/réservation public (mêmes conventions que BilletterieController) : seule la
 * connaissance du qrUuid du billet donne accès, exactement le même modèle de confiance que
 * le QR billet lui-même. Voir VoteSurPlaceService pour le détail anti-fraude.
 *
 * Blocage par appareil : {@code POST /appareil} émet un jeton signé, à renvoyer dans le header
 * {@code X-Appareil-Token} (obligatoire pour voter) ; {@code X-Appareil-Empreinte} (hash navigateur)
 * est facultatif et ne sert que de signal souple.
 */
@RestController
@RequestMapping("/vote-sur-place")
@RequiredArgsConstructor
public class VoteSurPlaceController {

    static final String HEADER_APPAREIL_TOKEN = "X-Appareil-Token";
    static final String HEADER_APPAREIL_EMPREINTE = "X-Appareil-Empreinte";

    private final VoteSurPlaceService voteSurPlaceService;
    private final AppareilVoteService appareilVoteService;

    /** Émet un jeton d'appareil signé. Aucune donnée persistée ; volume par IP journalisé (signal souple). */
    @PostMapping("/appareil")
    public ResponseEntity<AppareilTokenResponse> emettreAppareil(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(new AppareilTokenResponse(appareilVoteService.emettre(httpRequest.getRemoteAddr())));
    }

    @GetMapping("/{soireeId}/{qrUuid}")
    public ResponseEntity<DroitVoteResponse> consulter(
            @PathVariable UUID soireeId, @PathVariable UUID qrUuid,
            @RequestHeader(name = HEADER_APPAREIL_TOKEN, required = false) String appareilToken) {
        return ResponseEntity.ok(voteSurPlaceService.consulterDroit(qrUuid, soireeId, appareilToken));
    }

    /**
     * Header {@code X-Appareil-Token} volontairement {@code required=false} côté Spring : son absence
     * doit produire l'erreur métier explicite 400 {@code APPAREIL_INCONNU} (et non une 500 générique).
     */
    @PostMapping("/{soireeId}/{qrUuid}/voter")
    public ResponseEntity<DroitVoteResponse> voter(
            @PathVariable UUID soireeId, @PathVariable UUID qrUuid,
            @Valid @RequestBody VoterSurPlaceRequest request,
            @RequestHeader(name = HEADER_APPAREIL_TOKEN, required = false) String appareilToken,
            @RequestHeader(name = HEADER_APPAREIL_EMPREINTE, required = false) String empreinte,
            HttpServletRequest httpRequest) {
        var appareil = new AppareilVoteService.ContexteAppareil(
                appareilToken, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"), empreinte);
        return ResponseEntity.ok(voteSurPlaceService.voter(qrUuid, soireeId, request.candidatId(),
                request.telephoneVotant(), request.positionLatitude(), request.positionLongitude(),
                request.positionPrecisionM(), appareil));
    }
}
