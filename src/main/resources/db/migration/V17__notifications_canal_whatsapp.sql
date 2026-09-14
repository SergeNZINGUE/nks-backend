-- WhatsApp devient le canal prioritaire pour les notifications "SMS" existantes (le SMS
-- reste le fallback automatique si l'envoi WhatsApp échoue) : le CHECK sur notifications.canal
-- doit désormais accepter la valeur WHATSAPP en plus de SMS/EMAIL/IN_APP.
ALTER TABLE notifications DROP CONSTRAINT notifications_canal_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_canal_check
    CHECK (canal IN ('SMS','EMAIL','IN_APP','WHATSAPP'));
