package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Partenaire;
import bf.laterrasse.nks.domain.enums.Enums.StatutPartenaire;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.PartenaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** §13.13 — US-36. */
@RestController
@RequestMapping("/partenaires")
@RequiredArgsConstructor
public class PartenaireController {

    private final PartenaireRepository partenaireRepository;

    @GetMapping
    public ResponseEntity<List<Partenaire>> lister() {
        return ResponseEntity.ok(partenaireRepository.findByStatut(StatutPartenaire.ACTIF));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Partenaire> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(partenaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partenaire introuvable")));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Partenaire> creer(@RequestBody Partenaire partenaire) {
        partenaire.setId(null);
        return ResponseEntity.status(201).body(partenaireRepository.save(partenaire));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Partenaire> mettreAJour(@PathVariable UUID id, @RequestBody Partenaire modif) {
        Partenaire partenaire = partenaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partenaire introuvable"));
        partenaire.setNom(modif.getNom());
        partenaire.setLogoUrl(modif.getLogoUrl());
        partenaire.setDescription(modif.getDescription());
        partenaire.setSiteWebUrl(modif.getSiteWebUrl());
        partenaire.setNiveauPartenariat(modif.getNiveauPartenariat());
        partenaire.setContactNom(modif.getContactNom());
        partenaire.setContactEmail(modif.getContactEmail());
        partenaire.setContactTelephone(modif.getContactTelephone());
        return ResponseEntity.ok(partenaireRepository.save(partenaire));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> desactiver(@PathVariable UUID id) {
        Partenaire partenaire = partenaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partenaire introuvable"));
        partenaire.setStatut(StatutPartenaire.INACTIF);
        partenaireRepository.save(partenaire);
        return ResponseEntity.noContent().build();
    }
}
