-- Make game_server_entity.created_on NOT NULL, matching the entity's intent.
--
-- GameServerEntity.createdOn is annotated @CreationTimestamp, so Hibernate always populates it on
-- insert and the column is never legitimately null. The V1 baseline nonetheless declares it
-- nullable, because Hibernate 6's ddl-auto generated it that way and V1 captured that schema.
--
-- Hibernate 7 (Spring Boot 4.1+) emits NOT NULL for @CreationTimestamp columns, which makes the
-- generated schema and the baseline disagree. Pinning the constraint here settles it in the
-- migrations rather than leaving it dependent on the Hibernate version in use.
--
-- The UPDATE is a safety net for pre-Flyway deployments: their column has always been nullable, so
-- a row could hold NULL if it was ever inserted outside the entity (e.g. a manual backfill).
UPDATE public.game_server_entity SET created_on = now() WHERE created_on IS NULL;

ALTER TABLE public.game_server_entity ALTER COLUMN created_on SET NOT NULL;
