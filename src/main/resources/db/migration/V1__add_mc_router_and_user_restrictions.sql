-- V1: Add MC-Router integration and user restrictions
-- This migration adds support for:
-- 1. MC-Router configuration for Minecraft server routing
-- 2. User port restrictions
-- 3. User game server creation permissions
-- 4. User MC-Router domain restrictions

-- ============================================
-- USER ENTITY RESTRICTIONS
-- ============================================

-- Add port restrictions columns to user_entity (only if table already exists from Hibernate)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_entity') THEN
        ALTER TABLE user_entity ADD COLUMN IF NOT EXISTS port_restrictions_enabled BOOLEAN NOT NULL DEFAULT true;
        ALTER TABLE user_entity ADD COLUMN IF NOT EXISTS allow_game_server_creation BOOLEAN NOT NULL DEFAULT true;
        ALTER TABLE user_entity ADD COLUMN IF NOT EXISTS mc_router_allow_all_domains BOOLEAN NOT NULL DEFAULT false;
    END IF;
END $$;

-- Create collection tables for user restrictions (only if user_entity exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_entity') THEN
        CREATE TABLE IF NOT EXISTS user_allowed_ports (
            user_uuid VARCHAR(255) NOT NULL,
            port_range VARCHAR(255),
            CONSTRAINT fk_user_allowed_ports_user FOREIGN KEY (user_uuid)
                REFERENCES user_entity(uuid) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS user_mc_router_domains (
            user_uuid VARCHAR(255) NOT NULL,
            domain VARCHAR(255) NOT NULL,
            CONSTRAINT fk_user_mc_router_domains_user FOREIGN KEY (user_uuid)
                REFERENCES user_entity(uuid) ON DELETE CASCADE
        );
    END IF;
END $$;

-- ============================================
-- USER INVITE ENTITY RESTRICTIONS
-- ============================================

-- Add restriction columns to user_invite_entity (only if table already exists from Hibernate)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_invite_entity') THEN
        ALTER TABLE user_invite_entity ADD COLUMN IF NOT EXISTS port_restrictions_enabled BOOLEAN NOT NULL DEFAULT true;
        ALTER TABLE user_invite_entity ADD COLUMN IF NOT EXISTS allow_game_server_creation BOOLEAN NOT NULL DEFAULT true;
        ALTER TABLE user_invite_entity ADD COLUMN IF NOT EXISTS mc_router_allow_all_domains BOOLEAN NOT NULL DEFAULT false;
    END IF;
END $$;

-- Create collection tables for user invite restrictions (only if user_invite_entity exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_invite_entity') THEN
        CREATE TABLE IF NOT EXISTS user_invite_allowed_ports (
            user_invite_uuid VARCHAR(255) NOT NULL,
            port_range VARCHAR(255),
            CONSTRAINT fk_user_invite_allowed_ports_invite FOREIGN KEY (user_invite_uuid)
                REFERENCES user_invite_entity(uuid) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS user_invite_mc_router_domains (
            user_invite_uuid VARCHAR(255) NOT NULL,
            domain VARCHAR(255) NOT NULL,
            CONSTRAINT fk_user_invite_mc_router_domains_invite FOREIGN KEY (user_invite_uuid)
                REFERENCES user_invite_entity(uuid) ON DELETE CASCADE
        );
    END IF;
END $$;

-- ============================================
-- GAME SERVER MC-ROUTER DOMAINS
-- ============================================

-- Create collection table for game server MC-Router domains (only if game_server_entity exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'game_server_entity') THEN
        CREATE TABLE IF NOT EXISTS mc_router_server_domains (
            game_server_uuid VARCHAR(255) NOT NULL,
            domain VARCHAR(255) NOT NULL,
            CONSTRAINT fk_mc_router_server_domains_gs FOREIGN KEY (game_server_uuid)
                REFERENCES game_server_entity(uuid) ON DELETE CASCADE
        );
    END IF;
END $$;

-- ============================================
-- COSY INSTANCE SETTINGS (MC-Router Config)
-- ============================================

-- Create cosy_instance_settings_entity table for global settings
CREATE TABLE IF NOT EXISTS cosy_instance_settings_entity (
    id BIGSERIAL PRIMARY KEY,
    mc_router_enabled BOOLEAN DEFAULT false,
    mc_router_port INTEGER DEFAULT 25565
);

-- Create collection table for MC-Router available domains
CREATE TABLE IF NOT EXISTS mc_router_domains (
    cosy_instance_settings_id BIGINT NOT NULL,
    domain VARCHAR(255) NOT NULL,
    CONSTRAINT fk_mc_router_domains_settings FOREIGN KEY (cosy_instance_settings_id)
        REFERENCES cosy_instance_settings_entity(id) ON DELETE CASCADE
);

-- ============================================
-- UPDATE EXISTING DATA WITH DEFAULTS
-- ============================================

-- Ensure existing users have default values (only if table exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_entity') THEN
        UPDATE user_entity SET port_restrictions_enabled = false WHERE port_restrictions_enabled IS NULL;
        UPDATE user_entity SET allow_game_server_creation = true WHERE allow_game_server_creation IS NULL;
        UPDATE user_entity SET mc_router_allow_all_domains = false WHERE mc_router_allow_all_domains IS NULL;
    END IF;
END $$;

-- Ensure existing invites have default values (only if table exists)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_invite_entity') THEN
        UPDATE user_invite_entity SET port_restrictions_enabled = false WHERE port_restrictions_enabled IS NULL;
        UPDATE user_invite_entity SET allow_game_server_creation = true WHERE allow_game_server_creation IS NULL;
        UPDATE user_invite_entity SET mc_router_allow_all_domains = false WHERE mc_router_allow_all_domains IS NULL;
    END IF;
END $$;
