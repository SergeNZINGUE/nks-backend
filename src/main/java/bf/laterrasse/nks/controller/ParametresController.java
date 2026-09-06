package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.service.ParametrePlateformeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Paramètres publics lisibles sans authentification — permet au frontend de ne pas figer les prix en dur. */
@RestController
@RequestMapping("/parametres")
@RequiredArgsConstructor
public class ParametresController {

    private final ParametrePlateformeService parametrePlateformeService;

    @Value("${nks.inscription.frais-fcfa}")
    private int fraisInscriptionFcfa;

    @GetMapping("/publics")
    public ResponseEntity<Map<String, Object>> publics() {
        return ResponseEntity.ok(Map.of(
                "prixInscriptionFcfa", parametrePlateformeService.getInt("PRIX_INSCRIPTION_FCFA", fraisInscriptionFcfa),
                "prixVoteFcfa", parametrePlateformeService.getInt("PRIX_VOTE_FCFA", 100)
        ));
    }
}
