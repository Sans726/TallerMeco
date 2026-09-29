CREATE TABLE company (
    id bigint AUTO_INCREMENT PRIMARY KEY,
    name varchar(160) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT ck_company_name_not_blank CHECK (length(trim(name)) > 0)
);

CREATE TABLE workshop (
    id bigint AUTO_INCREMENT PRIMARY KEY,
    company_id bigint NOT NULL,
    name varchar(160) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_workshop_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT ck_workshop_name_not_blank CHECK (length(trim(name)) > 0),
    CONSTRAINT uq_workshop_company_name UNIQUE (company_id, name)
);

CREATE INDEX ix_workshop_company ON workshop(company_id);

ALTER TABLE customer
    ADD COLUMN alias varchar(120) NULL,
    ADD COLUMN alternative_contact_name varchar(160) NULL,
    ADD COLUMN birth_date date NULL,
    ADD COLUMN personal_phone varchar(30) NULL,
    ADD COLUMN work_phone varchar(30) NULL,
    ADD COLUMN personal_email varchar(254) NULL,
    ADD COLUMN work_email varchar(254) NULL,
    ADD COLUMN photo_reference varchar(512) NULL,
    ADD COLUMN street varchar(180) NULL,
    ADD COLUMN neighborhood varchar(120) NULL,
    ADD COLUMN municipality varchar(120) NULL,
    ADD COLUMN state varchar(120) NULL,
    ADD COLUMN postal_code varchar(12) NULL,
    ADD COLUMN personal_email_normalized varchar(254)
        AS (NULLIF(LOWER(TRIM(personal_email)), '')) PERSISTENT,
    ADD COLUMN work_email_normalized varchar(254)
        AS (NULLIF(LOWER(TRIM(work_email)), '')) PERSISTENT,
    ADD COLUMN personal_phone_normalized varchar(30)
        AS (NULLIF(REGEXP_REPLACE(TRIM(personal_phone), '[^0-9]+', ''), '')) PERSISTENT,
    ADD COLUMN work_phone_normalized varchar(30)
        AS (NULLIF(REGEXP_REPLACE(TRIM(work_phone), '[^0-9]+', ''), '')) PERSISTENT,
    ADD CONSTRAINT ck_customer_personal_email CHECK (personal_email IS NULL OR personal_email LIKE '%@%'),
    ADD CONSTRAINT ck_customer_work_email CHECK (work_email IS NULL OR work_email LIKE '%@%');

CREATE UNIQUE INDEX uq_customer_personal_email ON customer(personal_email_normalized);
CREATE UNIQUE INDEX uq_customer_work_email ON customer(work_email_normalized);
CREATE UNIQUE INDEX uq_customer_personal_phone ON customer(personal_phone_normalized);
CREATE UNIQUE INDEX uq_customer_work_phone ON customer(work_phone_normalized);

CREATE TABLE customer_workshop (
    customer_id bigint NOT NULL,
    workshop_id bigint NOT NULL,
    active boolean NOT NULL DEFAULT true,
    joined_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (customer_id, workshop_id),
    CONSTRAINT fk_customer_workshop_customer FOREIGN KEY (customer_id) REFERENCES customer(id),
    CONSTRAINT fk_customer_workshop_workshop FOREIGN KEY (workshop_id) REFERENCES workshop(id)
);

CREATE INDEX ix_customer_workshop_workshop ON customer_workshop(workshop_id, customer_id);
