-- Correction ponctuelle : les soirées passées à TERMINEE avant l'ajout de
-- ClassementService.appliquerEliminationsStatutProfil() n'ont pas déclenché
-- la mise à jour du statut_profil des candidats éliminés.
--
-- Conditions cumulatives — un candidat n'est touché que s'il est :
--   1. actuellement ACTIF (pas déjà ELIMINE, FINALISTE, etc.)
--   2. affecté à une poule dont la soirée est TERMINEE
--   3. dans une phase de type ELIMINATOIRES
--   4. marqué ELIMINE dans ses résultats de phase (statut_qualification)

UPDATE candidats
SET statut_profil = 'ELIMINE'
WHERE statut_profil = 'ACTIF'
  AND id IN (
      SELECT ap.candidat_id
      FROM affectations_poules ap
      JOIN poules             p  ON ap.poule_id   = p.id
      JOIN phases             ph ON p.phase_id    = ph.id
      JOIN soirees_events     se ON p.soiree_id   = se.id
      JOIN resultats_phase    rp ON rp.candidat_id = ap.candidat_id
                                AND rp.phase_id    = p.phase_id
      WHERE se.statut               = 'TERMINEE'
        AND ph.nom                  = 'ELIMINATOIRES'
        AND rp.statut_qualification = 'ELIMINE'
  );
