-- V9 : anti-fraude votes sur MSISDN payeur réel (RM-25) et publication contrôlée des notes jury (option B)
ALTER TABLE votes_payants
    ADD COLUMN fraude_detectee boolean NOT NULL DEFAULT false;

ALTER TABLE soirees_events
    ADD COLUMN resultats_publies boolean NOT NULL DEFAULT false;
