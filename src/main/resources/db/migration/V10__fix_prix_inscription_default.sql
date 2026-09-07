-- PRIX_INSCRIPTION_FCFA était initialisé à 0 dans V1 (valeur non tranchée).
-- Mis à jour à 15000 FCFA (valeur de production confirmée).
-- L'admin peut modifier cette valeur via l'interface d'administration.
UPDATE parametres_plateforme SET valeur = '15000' WHERE cle = 'PRIX_INSCRIPTION_FCFA' AND valeur = '0';
