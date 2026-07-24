-- v3 template descriptions may exceed 255 chars and must not be truncated.
ALTER TABLE template_entity ALTER COLUMN description TYPE text;
