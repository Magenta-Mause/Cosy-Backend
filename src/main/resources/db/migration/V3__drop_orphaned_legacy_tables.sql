-- Drop orphaned legacy tables and columns left behind by `ddl-auto: update`.
--
-- These objects are artifacts of a never-merged mc-router feature branch: entities that existed
-- on that branch were materialised into the schema by Hibernate `ddl-auto: update` on any database
-- that ran it (verified present on the maintainers' own production deployment), but the entities
-- never reached main, so no code in a released image maps them. Released images never created
-- them either, so for most self-hosted installs this migration is a pure no-op -- hence the
-- IF EXISTS guards everywhere.
--
-- Why the drop is safe:
--   * No entity in the current model maps these tables/columns (Hibernate `validate` ignores
--     unmapped tables, which is why they lingered undetected).
--   * Released images never created them -> no-op for the overwhelming majority of installs.
--   * They were verified present on the maintainers' own deployment, and on the production dump
--     the orphan tables carry no foreign-key constraints -- but we still drop children before
--     parents below so databases that DO have the FK (mc_router_domains.cosy_instance_settings_id
--     references cosy_instance_settings_entity) drop cleanly too.
--
-- This makes fresh and upgraded databases genuinely converge, so future reuse of any of these
-- names cannot cause silent divergence between installs.
--
-- NOTE: when the mc-router feature eventually merges, it MUST ship its own V<N> migration that
-- creates its tables/columns fresh -- it must not rely on the orphaned objects some databases
-- happen to still carry, because this migration has removed them everywhere.

-- Tables: drop children / collection tables first, parent (cosy_instance_settings_entity) last.
DROP TABLE IF EXISTS public.mc_router_domains;
DROP TABLE IF EXISTS public.mc_router_server_domains;
DROP TABLE IF EXISTS public.user_allowed_ports;
DROP TABLE IF EXISTS public.user_invite_allowed_ports;
DROP TABLE IF EXISTS public.user_invite_mc_router_domains;
DROP TABLE IF EXISTS public.user_mc_router_domains;
DROP TABLE IF EXISTS public.cosy_instance_settings_entity;

-- Orphan columns on the still-live user tables. DROP COLUMN IF EXISTS is a no-op when the column
-- is already absent, so these ALTERs are safe on databases that never grew the columns; the tables
-- themselves always exist in the current model.
ALTER TABLE public.user_entity
    DROP COLUMN IF EXISTS allow_game_server_creation,
    DROP COLUMN IF EXISTS mc_router_allow_all_domains,
    DROP COLUMN IF EXISTS port_restrictions_enabled;

ALTER TABLE public.user_invite_entity
    DROP COLUMN IF EXISTS allow_game_server_creation,
    DROP COLUMN IF EXISTS mc_router_allow_all_domains,
    DROP COLUMN IF EXISTS port_restrictions_enabled;
