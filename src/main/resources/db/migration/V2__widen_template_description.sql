-- Widen template_entity.description so v3 template descriptions (which may exceed 255 chars)
-- are not truncated.
--
-- This exists for pre-Flyway deployments whose `description` column is still varchar(255) from
-- the old entity definition: they are baselined at V1 with their existing schema, so this ALTER
-- is what actually widens them. On fresh installs it is a no-op -- V1 already creates the column
-- as `text` (matching the current entity), so there is nothing to change.
ALTER TABLE public.template_entity ALTER COLUMN description TYPE text;
