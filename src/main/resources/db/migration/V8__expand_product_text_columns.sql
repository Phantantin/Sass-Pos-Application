-- Product validation already accepts descriptions and uploaded-image URLs
-- longer than the original 255-character schema columns.
ALTER TABLE product MODIFY COLUMN description VARCHAR(1000) NULL;
ALTER TABLE product MODIFY COLUMN image VARCHAR(2048) NULL;
