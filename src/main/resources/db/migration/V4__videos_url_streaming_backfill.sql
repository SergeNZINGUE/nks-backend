-- Renseigne url_streaming pour les vidéos existantes dont le champ est null.
-- url_stockage_originale contient déjà l'URL Cloudinary lisible directement.
UPDATE videos
SET url_streaming = url_stockage_originale
WHERE url_streaming IS NULL
  AND url_stockage_originale IS NOT NULL;
