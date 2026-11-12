CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),

    CONSTRAINT chk_user_role CHECK (
        role IN ('USER', 'ADMIN')
    )
);

INSERT INTO users (
    username,
    email,
    password_hash,
    role
)
VALUES (
    'legacy_user',
    'legacy@marketlens.local',
    'NO_LOGIN',
    'USER'
);

ALTER TABLE reports
ADD COLUMN user_id BIGINT;

UPDATE reports
SET user_id = (
    SELECT id
    FROM users
    WHERE username = 'legacy_user'
);

ALTER TABLE reports
ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE reports
ADD CONSTRAINT fk_report_user
    FOREIGN KEY (user_id)
    REFERENCES users(id);

CREATE INDEX idx_reports_user
    ON reports(user_id);