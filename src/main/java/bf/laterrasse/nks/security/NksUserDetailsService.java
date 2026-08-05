package bf.laterrasse.nks.security;

import bf.laterrasse.nks.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NksUserDetailsService implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return utilisateurRepository.findByEmailIgnoreCaseAndDateSuppressionIsNull(email)
                .map(NksUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Aucun utilisateur actif pour l'email : " + email));
    }
}
