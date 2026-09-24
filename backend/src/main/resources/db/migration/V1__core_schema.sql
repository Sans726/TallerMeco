CREATE TABLE app_user (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 email varchar(254) COLLATE utf8mb4_bin NOT NULL,
 password_hash varchar(255) NOT NULL,
 enabled boolean NOT NULL DEFAULT true,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 version bigint NOT NULL DEFAULT 0,
 CHECK (email = lower(trim(email)) AND position('@' in email) > 1)
);
CREATE UNIQUE INDEX uq_user_email ON app_user(email);
CREATE TABLE role (code varchar(30) PRIMARY KEY);
INSERT INTO role VALUES ('ADMIN'), ('MECHANIC'), ('CLIENT');
CREATE TABLE user_role (
 user_id bigint REFERENCES app_user(id), role_code varchar(30) REFERENCES role(code),
 PRIMARY KEY (user_id, role_code)
);
CREATE TABLE password_reset_token (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 user_id bigint NOT NULL REFERENCES app_user(id),
 token_hash varchar(64) NOT NULL UNIQUE CHECK(length(token_hash)=64),
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 expires_at datetime(6) NOT NULL, used_at datetime(6),
 CHECK(expires_at > created_at)
);
CREATE TABLE customer (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 user_id bigint UNIQUE REFERENCES app_user(id),
 full_name varchar(160) NOT NULL CHECK(length(trim(full_name))>0),
 phone varchar(30), created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 active boolean NOT NULL DEFAULT true, version bigint NOT NULL DEFAULT 0
);
CREATE TABLE employee (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 user_id bigint NOT NULL UNIQUE REFERENCES app_user(id),
 full_name varchar(160) NOT NULL, job_title varchar(80) NOT NULL,
 active boolean NOT NULL DEFAULT true
);
CREATE TABLE vehicle (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 customer_id bigint NOT NULL REFERENCES customer(id),
 make varchar(60) NOT NULL, model varchar(80) NOT NULL,
 model_year smallint CHECK(model_year BETWEEN 1900 AND 2200),
 trim_level varchar(80), license_plate varchar(20),
 vin varchar(17) UNIQUE CHECK(vin IS NULL OR length(vin)=17),
 active boolean NOT NULL DEFAULT true, version bigint NOT NULL DEFAULT 0
);
CREATE INDEX ix_vehicle_customer ON vehicle(customer_id);
CREATE TABLE service_order (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 vehicle_id bigint NOT NULL REFERENCES vehicle(id),
 customer_id bigint NOT NULL REFERENCES customer(id),
 status varchar(30) NOT NULL DEFAULT 'RECEIVED'
 CHECK(status IN ('RECEIVED','DIAGNOSIS','AWAITING_APPROVAL','IN_PROGRESS','READY','DELIVERED','CANCELLED')),
 complaint text NOT NULL, diagnosis text,
 odometer_km integer CHECK(odometer_km >= 0),
 currency varchar(3) NOT NULL DEFAULT 'MXN' CHECK(currency REGEXP '^[A-Z]{3}$'),
 estimated_amount numeric(14,2) CHECK(estimated_amount >= 0),
 authorized_at datetime(6), authorization_method varchar(80),
 authorized_by bigint REFERENCES app_user(id),
 received_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), closed_at datetime(6),
 created_by bigint NOT NULL REFERENCES app_user(id),
 version bigint NOT NULL DEFAULT 0,
 CHECK(closed_at IS NULL OR closed_at >= received_at),
 CHECK((authorized_at IS NULL AND authorization_method IS NULL AND authorized_by IS NULL)
 OR (authorized_at IS NOT NULL AND authorization_method IS NOT NULL AND authorized_by IS NOT NULL))
);
CREATE INDEX ix_order_customer ON service_order(customer_id, received_at);
CREATE INDEX ix_order_vehicle ON service_order(vehicle_id, received_at);
CREATE INDEX ix_order_status ON service_order(status, received_at);
CREATE TABLE order_assignment (
 order_id bigint REFERENCES service_order(id), employee_id bigint REFERENCES employee(id),
 assigned_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), assigned_by bigint NOT NULL REFERENCES app_user(id),
 PRIMARY KEY(order_id, employee_id)
);
CREATE INDEX ix_assignment_employee ON order_assignment(employee_id);
CREATE TABLE work_entry (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 order_id bigint NOT NULL, employee_id bigint NOT NULL,
 description text NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','IN_PROGRESS','DONE','CANCELLED')),
 started_at datetime(6), finished_at datetime(6),
 direct_cost numeric(14,2) NOT NULL DEFAULT 0 CHECK(direct_cost >= 0),
 sale_price numeric(14,2) NOT NULL DEFAULT 0 CHECK(sale_price >= 0),
 FOREIGN KEY(order_id, employee_id) REFERENCES order_assignment(order_id, employee_id),
 CHECK(finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at))
);
CREATE TABLE part (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 sku varchar(60) NOT NULL UNIQUE, name varchar(160) NOT NULL,
 unit varchar(20) NOT NULL DEFAULT 'UNIT',
 minimum_stock numeric(14,3) NOT NULL DEFAULT 0 CHECK(minimum_stock >= 0),
 stock numeric(14,3) NOT NULL DEFAULT 0 CHECK(stock >= 0),
 reference_cost numeric(14,2) NOT NULL DEFAULT 0 CHECK(reference_cost >= 0),
 reference_price numeric(14,2) NOT NULL DEFAULT 0 CHECK(reference_price >= 0),
 active boolean NOT NULL DEFAULT true
);
-- Each signed movement is also the historical order consumption/return record.
CREATE TABLE inventory_movement (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 part_id bigint NOT NULL REFERENCES part(id),
 order_id bigint REFERENCES service_order(id),
 kind varchar(20) NOT NULL CHECK(kind IN ('RECEIPT','CONSUMPTION','RETURN','ADJUSTMENT')),
 quantity numeric(14,3) NOT NULL CHECK(quantity <> 0),
 unit_cost numeric(14,2) NOT NULL CHECK(unit_cost >= 0),
 unit_price numeric(14,2) NOT NULL DEFAULT 0 CHECK(unit_price >= 0),
 reason text NOT NULL CHECK(length(trim(reason))>0),
 actor_id bigint NOT NULL REFERENCES app_user(id),
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK((kind='RECEIPT' AND quantity > 0 AND order_id IS NULL)
 OR (kind='CONSUMPTION' AND quantity < 0 AND order_id IS NOT NULL)
 OR (kind='RETURN' AND quantity > 0 AND order_id IS NOT NULL)
 OR (kind='ADJUSTMENT' AND order_id IS NULL))
);
CREATE INDEX ix_movement_part ON inventory_movement(part_id, created_at);
CREATE INDEX ix_movement_order ON inventory_movement(order_id);
CREATE TABLE payment (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 order_id bigint NOT NULL REFERENCES service_order(id),
 kind varchar(10) NOT NULL CHECK(kind IN ('PAYMENT','REFUND')),
 amount numeric(14,2) NOT NULL CHECK(amount > 0),
 method varchar(20) NOT NULL CHECK(method IN ('CASH','CARD','TRANSFER')),
 reference varchar(120), reason text NOT NULL,
 actor_id bigint NOT NULL REFERENCES app_user(id),
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX ix_payment_order ON payment(order_id, created_at);
CREATE TABLE order_status_history (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 order_id bigint NOT NULL REFERENCES service_order(id),
 previous_status varchar(30), new_status varchar(30) NOT NULL,
 actor_id bigint NOT NULL REFERENCES app_user(id), reason text NOT NULL,
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX ix_history_order ON order_status_history(order_id, created_at);
CREATE TABLE audit_event (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 actor_id bigint REFERENCES app_user(id),
 action varchar(80) NOT NULL, entity_type varchar(80) NOT NULL,
 entity_id bigint, changes json NOT NULL DEFAULT ('{}'),
 created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE INDEX ix_audit_entity ON audit_event(entity_type, entity_id, created_at);

CREATE TRIGGER inventory_balance AFTER INSERT ON inventory_movement
FOR EACH ROW UPDATE part SET stock=stock+NEW.quantity WHERE id=NEW.part_id;
CREATE TRIGGER immutable_inventory_movement_update BEFORE UPDATE ON inventory_movement
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical records are append-only';
CREATE TRIGGER immutable_inventory_movement_delete BEFORE DELETE ON inventory_movement
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical records are append-only';
CREATE TRIGGER immutable_payment_update BEFORE UPDATE ON payment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical records are append-only';
CREATE TRIGGER immutable_payment_delete BEFORE DELETE ON payment
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical records are append-only';
CREATE TRIGGER immutable_order_status_history_update BEFORE UPDATE ON order_status_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical records are append-only';
CREATE TRIGGER immutable_order_status_history_delete BEFORE DELETE ON order_status_history
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical records are append-only';
CREATE TRIGGER immutable_audit_event_update BEFORE UPDATE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical records are append-only';
CREATE TRIGGER immutable_audit_event_delete BEFORE DELETE ON audit_event
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Historical records are append-only';
