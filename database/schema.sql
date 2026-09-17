CREATE DATABASE IF NOT EXISTS campus_recycle DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_recycle;

CREATE TABLE users (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 username VARCHAR(32) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 real_name VARCHAR(50) NOT NULL,
 phone VARCHAR(20) UNIQUE,
 role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER',
 status ENUM('ACTIVE','DISABLED') NOT NULL DEFAULT 'ACTIVE',
 total_points INT NOT NULL DEFAULT 0 CHECK (total_points >= 0),
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE categories (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(40) NOT NULL UNIQUE,
 point_factor DECIMAL(5,2) NOT NULL DEFAULT 1.00 CHECK (point_factor > 0),
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE items (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 owner_id BIGINT NOT NULL,
 category_id BIGINT NOT NULL,
 name VARCHAR(100) NOT NULL,
 description TEXT,
 condition_level ENUM('NEW','GOOD','FAIR','POOR') NOT NULL DEFAULT 'GOOD',
 original_price DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (original_price >= 0),
 current_price DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (current_price >= 0),
 estimated_recycle_price DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (estimated_recycle_price >= 0),
 process_type ENUM('SELL','GIVE','RECYCLE') NOT NULL,
 status ENUM('PENDING','APPLIED','PROCESSING','COMPLETED','OFFLINE','CANCELLED') NOT NULL DEFAULT 'PENDING',
 published_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at DATETIME NULL,
 CONSTRAINT fk_item_owner FOREIGN KEY(owner_id) REFERENCES users(id),
 CONSTRAINT fk_item_category FOREIGN KEY(category_id) REFERENCES categories(id),
 INDEX idx_item_search(category_id, process_type, status, published_at),
 INDEX idx_item_owner(owner_id)
) ENGINE=InnoDB;

CREATE TABLE trade_orders (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 item_id BIGINT NOT NULL,
 buyer_id BIGINT NOT NULL,
 seller_id BIGINT NOT NULL,
 process_type ENUM('SELL','GIVE') NOT NULL,
 amount DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (amount >= 0),
 status ENUM('APPLIED','PROCESSING','COMPLETED','CANCELLED','REJECTED') NOT NULL DEFAULT 'APPLIED',
 applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at DATETIME NULL,
 CONSTRAINT fk_trade_item FOREIGN KEY(item_id) REFERENCES items(id),
 CONSTRAINT fk_trade_buyer FOREIGN KEY(buyer_id) REFERENCES users(id),
 CONSTRAINT fk_trade_seller FOREIGN KEY(seller_id) REFERENCES users(id),
 INDEX idx_trade_status(status)
) ENGINE=InnoDB;

CREATE TABLE recycling_stations (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 name VARCHAR(80) NOT NULL UNIQUE,
 location VARCHAR(200) NOT NULL,
 phone VARCHAR(20),
 accepted_categories VARCHAR(255),
 status ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN'
) ENGINE=InnoDB;

CREATE TABLE recycling_orders (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 item_id BIGINT NOT NULL UNIQUE,
 applicant_id BIGINT NOT NULL,
 station_id BIGINT NOT NULL,
 status ENUM('APPLIED','APPOINTED','PROCESSING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'APPLIED',
 appointment_at DATETIME NULL,
 applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at DATETIME NULL,
 CONSTRAINT fk_recycle_order_item FOREIGN KEY(item_id) REFERENCES items(id),
 CONSTRAINT fk_recycle_order_user FOREIGN KEY(applicant_id) REFERENCES users(id),
 CONSTRAINT fk_recycle_order_station FOREIGN KEY(station_id) REFERENCES recycling_stations(id),
 INDEX idx_recycle_order_query(station_id,status,applied_at)
) ENGINE=InnoDB;

CREATE TABLE recycling_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 recycling_order_id BIGINT NOT NULL UNIQUE,
 category_id BIGINT NOT NULL,
 weight_kg DECIMAL(8,2) NOT NULL CHECK (weight_kg > 0),
 recycle_amount DECIMAL(10,2) NOT NULL DEFAULT 0 CHECK (recycle_amount >= 0),
 contribution_points INT NOT NULL CHECK (contribution_points > 0),
 recycled_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_record_order FOREIGN KEY(recycling_order_id) REFERENCES recycling_orders(id),
 CONSTRAINT fk_record_category FOREIGN KEY(category_id) REFERENCES categories(id)
) ENGINE=InnoDB;

CREATE TABLE eco_point_records (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 recycling_record_id BIGINT NULL,
 change_points INT NOT NULL CHECK (change_points <> 0),
 reason VARCHAR(200) NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_points_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_points_record FOREIGN KEY(recycling_record_id) REFERENCES recycling_records(id),
 INDEX idx_points_user_time(user_id,created_at)
) ENGINE=InnoDB;
