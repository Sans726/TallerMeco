-- Identifiers are domain identifiers, not lowercase prose. V1--V6 remain immutable.
ALTER TABLE customer DROP CONSTRAINT ck_customer_curp_lower, DROP CONSTRAINT ck_customer_rfc_lower;
ALTER TABLE workshop DROP CONSTRAINT ck_workshop_rfc_lower;
UPDATE customer SET curp=UPPER(curp),rfc=UPPER(rfc);
UPDATE workshop SET rfc=UPPER(rfc);
ALTER TABLE customer ADD CONSTRAINT ck_customer_curp_upper CHECK(curp IS NULL OR BINARY curp=BINARY UPPER(curp)), ADD CONSTRAINT ck_customer_rfc_upper CHECK(rfc IS NULL OR BINARY rfc=BINARY UPPER(rfc));
ALTER TABLE workshop ADD CONSTRAINT ck_workshop_rfc_upper CHECK(rfc IS NULL OR BINARY rfc=BINARY UPPER(rfc));
CREATE TABLE customer_status (
 id bigint AUTO_INCREMENT PRIMARY KEY,
 code varchar(40) COLLATE utf8mb4_bin NOT NULL UNIQUE CHECK(code REGEXP '^[A-Z][A-Z0-9_]{1,39}$'),
 description varchar(500) NOT NULL CHECK(length(trim(description))>0),
 allows_operations boolean NOT NULL CHECK(allows_operations IN (0,1)),
 system boolean NOT NULL DEFAULT false CHECK(system IN (0,1)),
 version bigint NOT NULL DEFAULT 0,
 CHECK(code NOT IN ('ACTIVE','SUSPENDED') OR (system=true AND allows_operations=(code='ACTIVE')))
);
CREATE TABLE vehicle_status LIKE customer_status;
-- Vehicle statuses are an independent catalog; IN_SERVICE is derived from orders.
ALTER TABLE vehicle_status ADD CONSTRAINT ck_vehicle_status_not_service CHECK(code<>'IN_SERVICE');
INSERT INTO customer_status(code,description,allows_operations,system) VALUES ('ACTIVE','Cliente habilitado para operar normalmente en el sistema.',true,true),('SUSPENDED','Cliente registrado pero temporalmente impedido de iniciar nuevas operaciones.',false,true);
INSERT INTO vehicle_status(code,description,allows_operations,system) VALUES ('ACTIVE','Vehículo habilitado para iniciar nuevas operaciones.',true,true),('SUSPENDED','Vehículo registrado pero temporalmente impedido de iniciar nuevas operaciones.',false,true);
ALTER TABLE customer ADD COLUMN status_id bigint NULL;
UPDATE customer SET status_id=(SELECT id FROM customer_status WHERE code=IF(customer.active,'ACTIVE','SUSPENDED'));
ALTER TABLE customer MODIFY status_id bigint NOT NULL DEFAULT 1, ADD CONSTRAINT fk_customer_status FOREIGN KEY(status_id) REFERENCES customer_status(id), DROP COLUMN active;
ALTER TABLE vehicle ADD COLUMN status_id bigint NULL, ADD COLUMN color varchar(60) NULL, ADD COLUMN odometer_km integer NULL CHECK(odometer_km BETWEEN 0 AND 2147483647);
UPDATE vehicle SET status_id=(SELECT id FROM vehicle_status WHERE code=IF(vehicle.active,'ACTIVE','SUSPENDED')),vin=CASE WHEN UPPER(TRIM(vin)) REGEXP '^[A-HJ-NPR-Z0-9]{17}$' THEN UPPER(TRIM(vin)) ELSE UPPER(vin) END,license_plate=UPPER(REPLACE(TRIM(license_plate),' ',''));
-- Unknown legacy data stays NULL. New writes enforce complete automotive data in the service.
ALTER TABLE vehicle MODIFY status_id bigint NOT NULL DEFAULT 1, ADD CONSTRAINT fk_vehicle_status FOREIGN KEY(status_id) REFERENCES vehicle_status(id), DROP COLUMN active;
ALTER TABLE vehicle ADD CONSTRAINT ck_vehicle_vin_upper CHECK(vin IS NULL OR BINARY vin=BINARY UPPER(vin));
CREATE INDEX ix_customer_status ON customer(status_id,id);
CREATE INDEX ix_vehicle_status ON vehicle(status_id,id);
DELIMITER $$
CREATE TRIGGER protect_customer_status_update BEFORE UPDATE ON customer_status FOR EACH ROW
BEGIN
 IF NEW.system<>OLD.system OR (OLD.system AND (BINARY NEW.code<>BINARY OLD.code OR NEW.allows_operations<>OLD.allows_operations)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='System status semantics are protected'; END IF;
END$$
CREATE TRIGGER protect_customer_status_delete BEFORE DELETE ON customer_status FOR EACH ROW
BEGIN
 IF OLD.system THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='System status cannot be deleted'; END IF;
END$$
CREATE TRIGGER protect_vehicle_status_update BEFORE UPDATE ON vehicle_status FOR EACH ROW
BEGIN
 IF NEW.system<>OLD.system OR (OLD.system AND (BINARY NEW.code<>BINARY OLD.code OR NEW.allows_operations<>OLD.allows_operations)) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='System status semantics are protected'; END IF;
END$$
CREATE TRIGGER protect_vehicle_status_delete BEFORE DELETE ON vehicle_status FOR EACH ROW
BEGIN
 IF OLD.system THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='System status cannot be deleted'; END IF;
END$$
DELIMITER ;
