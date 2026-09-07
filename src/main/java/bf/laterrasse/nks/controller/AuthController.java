package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.auth.ChangerMotDePasseRequest;
import bf.laterrasse.nks.dto.auth.LoginRequest;
import bf.laterrasse.nks.dto.auth.LoginResponse;
import bf.laterrasse.nks.dto.auth.RefreshRequest;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(httpRequest.getRemoteAddr(), request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/changer-mot-de-passe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changerMotDePasse(@Valid @RequestBody ChangerMotDePasseRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        authService.changerMotDePasse(userId, request);
        return ResponseEntity.noContent().build();
    }
}
