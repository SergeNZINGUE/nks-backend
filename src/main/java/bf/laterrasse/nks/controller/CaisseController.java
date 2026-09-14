package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.votesurplace.CaisseValiderRequest;
import bf.laterrasse.nks.dto.votesurplace.ConsommationBonusResponse;
import bf.laterrasse.nks.dto.votesurplace.DroitVoteResponse;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.VoteSurPlaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Action unique de l'hôtesse en salle : scanne le billet (si pas déjà fait) et active la
 * consommation en une seule fois — cf. VoteSurPlaceService pour la cinématique complète
 * (fusion scan/activation décidée le 13/09/2026 pour éviter le goulot d'étranglement d'un
 * point de caisse unique : plusieurs hôtesses peuvent appeler cet endpoint en parallèle,
 * chacune depuis son propre téléphone, à chaque service).
 */
@RestController
@RequiredArgsConstructor
public class CaisseController {

    private final VoteSurPlaceService voteSurPlaceService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/caisse/consommations")
    @PreAuthorize("hasAnyRole('HOTESSE','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<DroitVoteResponse> validerConsommation(@Valid @RequestBody CaisseValiderRequest request,
                                                                  HttpServletRequest httpRequest) {
        var hotesse = currentUserProvider.getCurrentUser();
        DroitVoteResponse response = voteSurPlaceService.validerConsommation(
                request.qrUuid(), request.soireeId(), hotesse,
                httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Bouton dédié "Ajouter une consommation" — distinct de l'activation d'entrée ci-dessus :
     * l'hôtesse scanne le même QR du billet à chaque commande supplémentaire au bar. Ne
     * débloque un vote bonus (et une notification WhatsApp) que par palier, cf.
     * VoteSurPlaceService#ajouterConsommationBonus.
     */
    @PostMapping("/caisse/consommations-bonus")
    @PreAuthorize("hasAnyRole('HOTESSE','ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ConsommationBonusResponse> ajouterConsommationBonus(
            @Valid @RequestBody CaisseValiderRequest request) {
        var hotesse = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(
                voteSurPlaceService.ajouterConsommationBonus(request.qrUuid(), request.soireeId(), hotesse));
    }
}
