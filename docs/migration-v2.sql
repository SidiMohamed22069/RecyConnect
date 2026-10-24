-- Migration manuelle pour passer une base v1 existante en v2.
-- Hibernate (ddl-auto=update) cree les nouvelles tables et colonnes tout seul,
-- mais ne change jamais le type d'une colonne existante et n'ajoute une
-- contrainte d'unicite qu'en l'absence de doublons. A executer AVANT le
-- premier demarrage de la v2, apres un pg_dump.

-- 1. Doublons de nom d'utilisateur ou de numero (doivent renvoyer 0 ligne).
SELECT username, COUNT(*) FROM users GROUP BY username HAVING COUNT(*) > 1;
SELECT phone,    COUNT(*) FROM users GROUP BY phone    HAVING COUNT(*) > 1;
-- S'il y en a, renommer / corriger les comptes concernes avant de continuer.

-- 2. Description d'annonce en texte long (varchar(255) -> text).
ALTER TABLE product ALTER COLUMN description TYPE text;

-- 3. Contraintes d'unicite (Hibernate les creera aussi; idempotent).
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_username ON users (username);
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_phone    ON users (phone);

-- 4. Verification apres demarrage: les tables v2 existent.
-- SELECT to_regclass('favorites'), to_regclass('reviews'),
--        to_regclass('search_alerts'), to_regclass('negotiation_history');
