ALTER TABLE app_user
    ADD COLUMN display_name varchar(160) NULL,
    ADD COLUMN phone varchar(30) NULL,
    ADD COLUMN birth_date date NULL,
    ADD COLUMN bio varchar(500) NULL,
    ADD COLUMN photo_reference varchar(80) NULL;
