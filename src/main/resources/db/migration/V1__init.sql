-- Store every time in UTC (JDBC connectionTimeZone=UTC, hibernate.jdbc.time_zone=UTC).

CREATE TABLE tb_user
(
    id             CHAR(36)      NOT NULL,
    datagsm_id     BIGINT        NOT NULL COMMENT 'DataGSM /userinfo id',
    email          VARCHAR(255)  NOT NULL,
    name           VARCHAR(50)   NOT NULL,
    student_number VARCHAR(4)    NULL,
    generation     INT           NULL,
    avatar_url     VARCHAR(2048) NULL,
    role           VARCHAR(10)   NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    CONSTRAINT pk_tb_user PRIMARY KEY (id),
    CONSTRAINT uq_tb_user_datagsm_id UNIQUE (datagsm_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE tb_session
(
    id         CHAR(64)    NOT NULL COMMENT 'SHA-256 hex of the session cookie value',
    user_id    CHAR(36)    NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_tb_session PRIMARY KEY (id),
    CONSTRAINT fk_tb_session_user FOREIGN KEY (user_id) REFERENCES tb_user (id) ON DELETE CASCADE,
    INDEX idx_tb_session_expires_at (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE tb_jam
(
    id           CHAR(36)      NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    description  TEXT          NOT NULL,
    theme        VARCHAR(100)  NOT NULL,
    cover_url    VARCHAR(2048) NULL,
    start_at     DATETIME(6)   NOT NULL,
    end_at       DATETIME(6)   NOT NULL,
    host_id      CHAR(36)      NOT NULL,
    announced_at DATETIME(6)   NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    CONSTRAINT pk_tb_jam PRIMARY KEY (id),
    CONSTRAINT fk_tb_jam_user FOREIGN KEY (host_id) REFERENCES tb_user (id),
    INDEX idx_tb_jam_start_at (start_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE tb_game
(
    id              CHAR(36)      NOT NULL,
    jam_id          CHAR(36)      NOT NULL,
    owner_id        CHAR(36)      NOT NULL,
    title           VARCHAR(40)   NOT NULL,
    tagline         VARCHAR(80)   NOT NULL,
    description     TEXT          NOT NULL,
    genre           VARCHAR(20)   NOT NULL,
    icon_url        VARCHAR(2048) NULL,
    orientation     VARCHAR(10)   NOT NULL,
    env_platforms   VARCHAR(50)   NOT NULL COMMENT 'Comma-separated Platform enum names',
    env_inputs      VARCHAR(100)  NOT NULL COMMENT 'Comma-separated InputDevice enum names',
    env_note        VARCHAR(500)  NOT NULL,
    build_path      VARCHAR(100)  NULL COMMENT '{gameId}/{uploadId} under the build storage root',
    build_name      VARCHAR(200)  NULL,
    build_file_name VARCHAR(255)  NULL,
    review_status   VARCHAR(10)   NOT NULL,
    reject_reason   VARCHAR(500)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_tb_game PRIMARY KEY (id),
    CONSTRAINT fk_tb_game_jam FOREIGN KEY (jam_id) REFERENCES tb_jam (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_game_user FOREIGN KEY (owner_id) REFERENCES tb_user (id),
    INDEX idx_tb_game_jam_id (jam_id),
    INDEX idx_tb_game_owner_id (owner_id),
    INDEX idx_tb_game_review_status_created_at (review_status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE tb_jam_award
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    jam_id     CHAR(36)    NOT NULL,
    game_id    CHAR(36)    NOT NULL,
    award_rank INT         NOT NULL COMMENT 'Overall rank at announcement time',
    title      VARCHAR(50) NOT NULL,
    CONSTRAINT pk_tb_jam_award PRIMARY KEY (id),
    CONSTRAINT fk_tb_jam_award_jam FOREIGN KEY (jam_id) REFERENCES tb_jam (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_jam_award_game FOREIGN KEY (game_id) REFERENCES tb_game (id) ON DELETE CASCADE,
    CONSTRAINT uq_tb_jam_award_jam_id_game_id UNIQUE (jam_id, game_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE tb_review
(
    id               CHAR(36)      NOT NULL,
    game_id          CHAR(36)      NOT NULL,
    author_id        CHAR(36)      NOT NULL,
    score_fun        TINYINT       NOT NULL,
    score_theme      TINYINT       NOT NULL,
    score_polish     TINYINT       NOT NULL,
    score_creativity TINYINT       NOT NULL,
    comment          VARCHAR(1000) NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    CONSTRAINT pk_tb_review PRIMARY KEY (id),
    CONSTRAINT fk_tb_review_game FOREIGN KEY (game_id) REFERENCES tb_game (id) ON DELETE CASCADE,
    CONSTRAINT fk_tb_review_user FOREIGN KEY (author_id) REFERENCES tb_user (id),
    CONSTRAINT uq_tb_review_game_id_author_id UNIQUE (game_id, author_id),
    INDEX idx_tb_review_author_id (author_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
