-- V1__initial_version.sql

CREATE TABLE user (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    password_version INT NOT NULL DEFAULT 0,
    full_name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    password_reset_token_hash VARCHAR(255),
    password_reset_expires_at VARCHAR(255),
    verify_code VARCHAR(255),
    points INT NOT NULL DEFAULT 0,
    is_admin BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE user_follows_user (
    follower_id INT UNSIGNED NOT NULL,
    followed_id INT UNSIGNED NOT NULL,
    created_at DATETIME DEFAULT NOW(),
    PRIMARY KEY(follower_id, followed_id),
    FOREIGN KEY (follower_id) REFERENCES user(id),
    FOREIGN KEY (followed_id) REFERENCES user(id)
);


CREATE TABLE video (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    author INT UNSIGNED NOT NULL,
    video_name VARCHAR(255) NOT NULL,
    video_desc LONGTEXT,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    is_published BOOLEAN NOT NULL,
    visibility TINYINT NOT NULL DEFAULT 1,
    FOREIGN KEY (author) REFERENCES user(id)
);

CREATE TABLE user_can_view_video (
    user_id INT UNSIGNED NOT NULL,
    video_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, video_id),
    FOREIGN KEY (user_id) REFERENCES user(id),
    FOREIGN KEY (video_id) REFERENCES video(id)
);

CREATE TABLE multipart_upload (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY ,
    upload_id VARCHAR(255) NOT NULL,
    upload_key VARCHAR(255) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    owner_id INT UNSIGNED NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'pending',
    created_at DATETIME NOT NULL DEFAULT NOW(),
    FOREIGN KEY (owner_id) REFERENCES user(id)
);

CREATE TABLE user_likes_video (
    video_id INT UNSIGNED NOT NULL,
    user_id INT UNSIGNED NOT NULL,
    PRIMARY KEY (video_id, user_id),
    FOREIGN KEY (video_id) REFERENCES video(id),
    FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE TABLE points_history (
    transaction_id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    modifier INT NOT NULL,
    description VARCHAR(255) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES user(id)
)