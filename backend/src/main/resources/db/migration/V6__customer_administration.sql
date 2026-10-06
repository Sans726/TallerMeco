-- Preserve legacy records: unknown identity/address fields stay NULL.
-- Conflicting historical contacts abort this migration rather than merging customer identities.
ALTER TABLE customer
 ADD COLUMN given_name varchar(50) NULL,
 ADD COLUMN paternal_surname varchar(50) NULL,
 ADD COLUMN maternal_surname varchar(50) NULL,
 ADD COLUMN curp varchar(18) COLLATE utf8mb4_bin NULL,
 ADD COLUMN rfc varchar(13) COLLATE utf8mb4_bin NULL,
 ADD COLUMN cell_phone varchar(10) NULL,
 ADD CONSTRAINT ck_customer_curp_lower CHECK(curp IS NULL OR curp=LOWER(curp)),
 ADD CONSTRAINT ck_customer_rfc_lower CHECK(rfc IS NULL OR rfc=LOWER(rfc)),
 ADD CONSTRAINT ck_customer_cell_phone CHECK(cell_phone IS NULL OR cell_phone REGEXP '^[0-9]{10}$'),
 ADD CONSTRAINT uq_customer_curp UNIQUE(curp),
 ADD CONSTRAINT uq_customer_rfc UNIQUE(rfc);
UPDATE customer SET full_name=REPLACE(LOWER(REGEXP_REPLACE(TRIM(full_name), ' +', ' ')), '’', CHAR(39)),
 alias=LOWER(TRIM(alias)), alternative_contact_name=LOWER(TRIM(alternative_contact_name)),
 personal_email=LOWER(TRIM(personal_email)),work_email=LOWER(TRIM(work_email)),
 street=LOWER(TRIM(street)),neighborhood=LOWER(TRIM(neighborhood)),municipality=LOWER(TRIM(municipality)),state=LOWER(TRIM(state));
CREATE UNIQUE INDEX uq_customer_name_birth ON customer(full_name,birth_date);
CREATE UNIQUE INDEX uq_customer_cell_phone ON customer(cell_phone);
CREATE TABLE customer_contact (
 kind varchar(10) NOT NULL CHECK(kind IN ('email','phone')),
 value varchar(254) COLLATE utf8mb4_bin NOT NULL,
 customer_id bigint NOT NULL,
 PRIMARY KEY(kind,value),
 FOREIGN KEY(customer_id) REFERENCES customer(id)
);
CREATE INDEX ix_customer_contact_customer ON customer_contact(customer_id);
INSERT INTO customer_contact(kind,value,customer_id)
 SELECT DISTINCT kind,CASE WHEN kind='phone' AND LENGTH(value)=12 AND LEFT(value,2)='52' THEN SUBSTRING(value,3) ELSE value END,customer_id FROM (
 SELECT 'email' kind,personal_email_normalized value,id customer_id FROM customer
 UNION ALL SELECT 'email',work_email_normalized,id FROM customer
 UNION ALL SELECT 'phone',personal_phone_normalized,id FROM customer
 UNION ALL SELECT 'phone',work_phone_normalized,id FROM customer
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(TRIM(phone),'[^0-9]+',''),''),id FROM customer
 UNION ALL SELECT 'email',u.email,c.id FROM customer c JOIN app_user u ON u.id=c.user_id
 ) contacts WHERE value IS NOT NULL;
ALTER TABLE workshop
 ADD COLUMN legal_name varchar(160) NULL,
 ADD COLUMN rfc varchar(13) COLLATE utf8mb4_bin NULL,
 ADD COLUMN phone varchar(10) NULL,
 ADD COLUMN email varchar(254) NULL,
 ADD COLUMN street varchar(180) NULL,
 ADD COLUMN neighborhood varchar(120) NULL,
 ADD COLUMN municipality varchar(120) NULL,
 ADD COLUMN state varchar(120) NULL,
 ADD COLUMN postal_code varchar(5) NULL,
 ADD COLUMN banner_reference varchar(64) NULL,
 ADD COLUMN version bigint NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_workshop_rfc_lower CHECK(rfc IS NULL OR rfc=LOWER(rfc)),
 ADD CONSTRAINT ck_workshop_postal CHECK(postal_code IS NULL OR postal_code REGEXP '^[0-9]{5}$'),
 ADD CONSTRAINT uq_workshop_rfc UNIQUE(rfc);
UPDATE workshop SET name=LOWER(REGEXP_REPLACE(TRIM(name),' +',' '));
CREATE UNIQUE INDEX uq_workshop_name_legal ON workshop(name,legal_name);
CREATE TABLE user_workshop (
 user_id bigint NOT NULL,
 workshop_id bigint NOT NULL,
 active boolean NOT NULL DEFAULT true,
 PRIMARY KEY(user_id,workshop_id),
 FOREIGN KEY(user_id) REFERENCES app_user(id),
 FOREIGN KEY(workshop_id) REFERENCES workshop(id)
);
CREATE INDEX ix_user_workshop_workshop ON user_workshop(workshop_id,user_id);
-- Reception scopes are explicitly assigned by ADMIN; no automatic cross-workshop access.

DELIMITER $$
CREATE TRIGGER customer_contact_insert AFTER INSERT ON customer FOR EACH ROW
BEGIN
 INSERT INTO customer_contact(kind,value,customer_id)
 SELECT DISTINCT kind,CASE WHEN kind='phone' AND LENGTH(value)=12 AND LEFT(value,2)='52' THEN SUBSTRING(value,3) ELSE value END,NEW.id FROM (
 SELECT 'email' kind,NULLIF(LOWER(TRIM(NEW.personal_email)),'') value
 UNION ALL SELECT 'email',NULLIF(LOWER(TRIM(NEW.work_email)),'')
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(NEW.personal_phone,'[^0-9]+',''),'')
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(NEW.work_phone,'[^0-9]+',''),'')
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(NEW.cell_phone,'[^0-9]+',''),'')
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(NEW.phone,'[^0-9]+',''),'')
 UNION ALL SELECT 'email',email FROM app_user WHERE id=NEW.user_id
 ) values_to_reserve WHERE value IS NOT NULL;
END$$
CREATE TRIGGER customer_contact_update AFTER UPDATE ON customer FOR EACH ROW
BEGIN
 DELETE FROM customer_contact WHERE customer_id=NEW.id;
 INSERT INTO customer_contact(kind,value,customer_id)
 SELECT DISTINCT kind,CASE WHEN kind='phone' AND LENGTH(value)=12 AND LEFT(value,2)='52' THEN SUBSTRING(value,3) ELSE value END,NEW.id FROM (
 SELECT 'email' kind,NULLIF(LOWER(TRIM(NEW.personal_email)),'') value
 UNION ALL SELECT 'email',NULLIF(LOWER(TRIM(NEW.work_email)),'')
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(NEW.personal_phone,'[^0-9]+',''),'')
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(NEW.work_phone,'[^0-9]+',''),'')
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(NEW.cell_phone,'[^0-9]+',''),'')
 UNION ALL SELECT 'phone',NULLIF(REGEXP_REPLACE(NEW.phone,'[^0-9]+',''),'')
 UNION ALL SELECT 'email',email FROM app_user WHERE id=NEW.user_id
 ) values_to_reserve WHERE value IS NOT NULL;
END$$
DELIMITER ;
