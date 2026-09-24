ALTER TABLE inventory_movement ADD COLUMN reversal_of bigint NULL REFERENCES inventory_movement(id);
CREATE INDEX ix_movement_reversal ON inventory_movement(reversal_of);
ALTER TABLE payment ADD COLUMN request_key varchar(36) NULL UNIQUE;
ALTER TABLE inventory_movement ADD COLUMN request_key varchar(36) NULL UNIQUE;
