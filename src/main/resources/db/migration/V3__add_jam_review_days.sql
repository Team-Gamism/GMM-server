-- Upload review (approve/reject) is gone: every submission goes public once the jam's submissions close,
-- then it is rated for review_days days. Existing jams get 7 days, the frontend's default.
ALTER TABLE tb_jam
    ADD COLUMN review_days INT NOT NULL DEFAULT 7;

ALTER TABLE tb_game
    DROP INDEX idx_tb_game_review_status_created_at,
    DROP COLUMN review_status,
    DROP COLUMN reject_reason;
