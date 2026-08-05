package bf.laterrasse.nks.security;

import bf.laterrasse.nks.domain.Utilisateur;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapte {@link Utilisateur} au contrat Spring Security. Les rôles sont exposés avec le
 * préfixe ROLE_ requis par {@code hasRole(...)} / {@code @PreAuthorize("hasRole(...)")}.
 */
@Getter
public class NksUserPrincipal implements UserDetails {

    private final Utilisateur utilisateur;

    public NksUserPrincipal(Utilisateur utilisateur) {
        this.utilisateur = utilisateur;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return utilisateur.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getNom().name()))
                .toList();
    }

    public static Collection<? extends GrantedAuthority> authoritiesFromRoleNames(List<String> roleNames) {
        return roleNames.stream()
                .map(name -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + name))
                .toList();
    }

    @Override
    public String getPassword() {
        return utilisateur.getMotDePasseHash();
    }

    @Override
    public String getUsername() {
        return utilisateur.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !"SUSPENDU".equals(utilisateur.getStatut().name());
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIF".equals(utilisateur.getStatut().name()) && !utilisateur.estSupprime();
    }
}
