-- Optimistic lock for tb_game. Concurrent edits, build replacements and upload reviews of one game
-- used to overwrite each other; the stale write now fails with 409 instead.
ALTER TABLE tb_game
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
