package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.billetterie.OtpDemanderRequest;
import bf.laterrasse.nks.dto.billetterie.OtpDemanderResponse;
import bf.laterrasse.nks.dto.billetterie.OtpVerifierRequest;
import bf.laterrasse.nks.dto.billetterie.OtpVerifierResponse;
import bf.laterrasse.nks.service.OtpTicketVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correctif audit sécurité (IDOR billetterie) : vérification par code OTP avant de
 * remettre un jeton d'accès billets (voir {@link BilletterieController} pour les 3
 * endpoints désormais gatés par ce jeton).
 */
@RestController
@RequestMapping("/reservations/mes-tickets/otp")
@RequiredArgsConstructor
public class OtpTicketController {

    private final OtpTicketVerificationService otpTicketVerificationService;

    @PostMapping("/demander")
    public ResponseEntity<OtpDemanderResponse> demander(@Valid @RequestBody OtpDemanderRequest request,
                                                         HttpServletRequest httpRequest) {
        otpTicketVerificationService.demander(request.telephone(), httpRequest.getRemoteAddr());
        // Réponse toujours 200 générique — ne jamais révéler si le numéro existe/a des réservations.
        return ResponseEntity.ok(OtpDemanderResponse.generique());
    }

    @PostMapping("/verifier")
    public ResponseEntity<OtpVerifierResponse> verifier(@Valid @RequestBody OtpVerifierRequest request) {
        return ResponseEntity.ok(otpTicketVerificationService.verifier(request.telephone(), request.code()));
    }
}
