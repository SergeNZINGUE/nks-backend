package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.RefreshToken;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.dto.auth.LoginRequest;
import bf.laterrasse.nks.dto.auth.LoginResponse;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.repository.RefreshTokenRepository;
import bf.laterrasse.nks.repository.UtilisateurRepository;
import bf.laterrasse.nks.security.JwtProperties;
import bf.laterrasse.nks.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WF Auth : login (5 tentatives / 15 min / IP, cf. §14.8 — appliqué au niveau du
 * RateLimitService/filtre HTTP, pas ici), refresh (rotation), logout (révocation).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Utilisateur utilisateur = utilisateurRepository
                .findByEmailIgnoreCaseAndDateSuppressionIsNull(request.email())
                .orElseThrow(() -> new AccesRefuseException("Identifiants invalides"));

        if (!passwordEncoder.matches(request.motDePasse(), utilisateur.getMotDePasseHash())) {
            throw new AccesRefuseException("Identifiants invalides");
        }
        if (utilisateur.getStatut() != bf.laterrasse.nks.domain.enums.Enums.StatutUtilisateur.ACTIF) {
            throw new AccesRefuseException("Compte suspendu ou inactif");
        }

        utilisateur.setDateDerniereConnexion(Instant.now());
        utilisateurRepository.save(utilisateur);

        String accessToken = jwtService.generateAccessToken(utilisateur);
        String refreshToken = issueRefreshToken(utilisateur);

        List<String> roles = utilisateur.getRoles().stream()
                .map(r -> r.getNom().name())
                .collect(Collectors.toList());

        return new LoginResponse(accessToken, refreshToken,
                jwtProperties.getAccessTokenExpirationMinutes() * 60, roles);
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken) {
        String hash = hash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevoqueFalse(hash)
                .orElseThrow(() -> new AccesRefuseException("Refresh token invalide"));

        if (stored.getDateExpiration().isBefore(Instant.now())) {
            throw new AccesRefuseException("Refresh token expiré");
        }

        // Rotation : l'ancien token est révoqué, un nouveau est émis
        stored.setRevoque(true);
        refreshTokenRepository.save(stored);

        Utilisateur utilisateur = stored.getUtilisateur();
        String accessToken = jwtService.generateAccessToken(utilisateur);
        String newRefreshToken = issueRefreshToken(utilisateur);

        List<String> roles = utilisateur.getRoles().stream()
                .map(r -> r.getNom().name())
                .collect(Collectors.toList());

        return new LoginResponse(accessToken, newRefreshToken,
                jwtProperties.getAccessTokenExpirationMinutes() * 60, roles);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHashAndRevoqueFalse(hash).ifPresent(token -> {
            token.setRevoque(true);
            refreshTokenRepository.save(token);
        });
    }

    private String issueRefreshToken(Utilisateur utilisateur) {
        byte[] randomBytes = new byte[64];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken refreshToken = RefreshToken.builder()
                .utilisateur(utilisateur)
                .tokenHash(hash(rawToken))
                .revoque(false)
                .dateExpiration(Instant.now().plus(jwtProperties.getRefreshTokenExpirationDays(), ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes());
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de hashage du refresh token", e);
        }
    }
}
