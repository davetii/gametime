-- liquibase formatted sql

-- changeset dave:1.0.0 failOnError:true splitStatements:false dbms:postgresql

CREATE OR REPLACE FUNCTION gametime.on_new_row()
RETURNS TRIGGER AS $$

BEGIN
    NEW.create_user := current_user;
    NEW.create_date := NOW();
    NEW.update_user := current_user;
    NEW.update_date := NOW();
    RETURN NEW;
END;

$$ LANGUAGE plpgsql;
