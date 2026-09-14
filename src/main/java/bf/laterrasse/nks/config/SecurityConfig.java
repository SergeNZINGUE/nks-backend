package bf.laterrasse.nks.config;

import bf.laterrasse.nks.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;

/**
 * RBAC (§14.3) : 8 rôles, JWT stateless côté vérification mais refresh tokens stateful
 * (ADR-06) pour permettre la révocation. Toute route non listée explicitement en lecture
 * publique exige une authentification ; le contrôle fin par rôle est fait via
 * @PreAuthorize sur chaque contrôleur (défense en profondeur).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    @Value("${nks.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/candidats/**", "/videos/candidat/**", "/medias/candidat/**", "/editions/**", "/soirees/**",
            "/partenaires/**", "/classement/**", "/resultats/**", "/votes/candidat/**",
            "/duos/phase/**", "/poules/*/candidats", "/poules/phase/**", "/docs/**", "/api-docs/**",
            "/swagger-ui/**", "/swagger-ui.html", "/actuator/health",
            "/paiements/*/statut-public", "/parametres/publics"
    };

    private static final String[] PUBLIC_ALL_METHODS_ENDPOINTS = {
            "/auth/**", "/webhooks/**", "/candidatures", "/medias/url-upload", "/videos/url-upload",
            "/medias/*/confirmer", "/videos/*/confirmer", "/votes/initier",
            "/reservations/initier", "/reservations/mes-tickets", "/reservations/*/ticket",
            "/reservations/mes-tickets/otp/**",
            "/vote-sur-place/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless consommée par un frontend séparé (pas de cookies de session)
                .addFilterBefore(privateNetworkAccessFilter(), org.springframework.web.filter.CorsFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                        .requestMatchers(PUBLIC_ALL_METHODS_ENDPOINTS).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/reservations/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Ticket-Access-Token"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Private Network Access (PNA) — Spring Framework 6.1 (Boot 3.3) n'a pas encore
     * `CorsConfiguration.setAllowPrivateNetwork` (ajouté en 6.2/Boot 3.4), donc on répond
     * nous-mêmes au header de préflight `Access-Control-Request-Private-Network`. Ne couvre
     * que l'ancien mécanisme basé sur un header CORS — le mécanisme plus récent de certains
     * navigateurs (permission utilisateur explicite, type caméra/micro) n'est pas contournable
     * côté serveur, quel que soit ce qu'on renvoie ici.
     */
    @Bean
    public OncePerRequestFilter privateNetworkAccessFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                             jakarta.servlet.http.HttpServletResponse response,
                                             jakarta.servlet.FilterChain chain)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                if ("true".equalsIgnoreCase(request.getHeader("Access-Control-Request-Private-Network"))) {
                    response.setHeader("Access-Control-Allow-Private-Network", "true");
                }
                chain.doFilter(request, response);
            }
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // cost factor 12 minimum — §14.2
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
