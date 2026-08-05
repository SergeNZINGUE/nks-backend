package bf.laterrasse.nks.service;

import bf.laterrasse.nks.repository.ParametrePlateformeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Accès typé aux paramètres modifiables par l'admin (table parametres_plateforme).
 * Utilisé notamment pour les valeurs décidées avec le client mais volontairement
 * configurables plutôt que figées en dur : tarif d'inscription, seuil anti-fraude votes.
 */
@Service
@RequiredArgsConstructor
public class ParametrePlateformeService {

    private final ParametrePlateformeRepository repository;

    public int getInt(String cle, int defaut) {
        return repository.findByCle(cle)
                .map(p -> Integer.parseInt(p.getValeur()))
                .orElse(defaut);
    }

    public boolean getBoolean(String cle, boolean defaut) {
        return repository.findByCle(cle)
                .map(p -> Boolean.parseBoolean(p.getValeur()))
                .orElse(defaut);
    }

    public String getString(String cle, String defaut) {
        return repository.findByCle(cle)
                .map(bf.laterrasse.nks.domain.ParametrePlateforme::getValeur)
                .orElse(defaut);
    }
}
