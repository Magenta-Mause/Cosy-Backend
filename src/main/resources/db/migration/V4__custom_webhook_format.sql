-- Custom webhook format (issue #44): a webhook may now define its own HTTP method, headers and
-- body template instead of using one of the built-in integration payloads.
--
-- Every statement is guarded (IF EXISTS / IF NOT EXISTS) because this migration has to survive two
-- different starting points:
--   * a fresh install, where V1 created webhook_entity without any of these objects, and
--   * a self-hosted upgrade whose schema was grown by `ddl-auto: update` -- such a database may
--     ALREADY carry the new columns and the headers table (Hibernate created them on an earlier
--     boot), but it still carries the OLD webhook_type check constraint, which Hibernate never
--     rewrites. Dropping and recreating that constraint is the part that actually matters there.

-- Allow the new CUSTOM discriminator. The constraint name matches the one V1 baselines, which is
-- also the name Hibernate generates for enum columns (<table>_<column>_check).
ALTER TABLE public.webhook_entity
    DROP CONSTRAINT IF EXISTS webhook_entity_webhook_type_check;

ALTER TABLE public.webhook_entity
    ADD CONSTRAINT webhook_entity_webhook_type_check
    CHECK (webhook_type IN ('DISCORD', 'SLACK', 'N8N', 'CUSTOM'));

-- Request method and body template. Both stay nullable: they are meaningless for the built-in
-- integration types, and NULL is the honest representation of "not applicable" for every row that
-- exists today.
ALTER TABLE public.webhook_entity
    ADD COLUMN IF NOT EXISTS http_method character varying(255),
    ADD COLUMN IF NOT EXISTS body_template text;

ALTER TABLE public.webhook_entity
    DROP CONSTRAINT IF EXISTS webhook_entity_http_method_check;

ALTER TABLE public.webhook_entity
    ADD CONSTRAINT webhook_entity_http_method_check
    CHECK (http_method IS NULL OR http_method IN
        ('GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'));

-- Header map for custom webhooks. header_value is varchar(2048) rather than the default 255
-- because header values commonly carry bearer tokens and signatures.
CREATE TABLE IF NOT EXISTS public.game_server_webhook_headers (
    webhook_id character varying(255) NOT NULL,
    header_name character varying(255) NOT NULL,
    header_value character varying(2048) NOT NULL,
    CONSTRAINT game_server_webhook_headers_pkey PRIMARY KEY (webhook_id, header_name),
    CONSTRAINT game_server_webhook_headers_webhook_id_fkey
        FOREIGN KEY (webhook_id) REFERENCES public.webhook_entity(uuid)
);
