-- liquibase formatted sql

-- changeset dave:1.02.1 failOnError:true splitStatements:true
ALTER TABLE gametime.player ADD COLUMN verticality SMALLINT DEFAULT 10;
ALTER TABLE gametime.player ADD COLUMN wingspan SMALLINT DEFAULT 10;
ALTER TABLE gametime.player ADD COLUMN composure SMALLINT DEFAULT 10;
ALTER TABLE gametime.player ADD COLUMN aggression SMALLINT DEFAULT 10;
ALTER TABLE gametime.player ADD COLUMN awareness SMALLINT DEFAULT 10;
