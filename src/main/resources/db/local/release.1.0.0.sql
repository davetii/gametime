-- liquibase formatted sql

-- changeset dave:1.01 failOnError:true splitStatements:false

CREATE OR REPLACE FUNCTION on_new_row()
RETURNS TRIGGER AS $$

BEGIN
    NEW.create_user := current_user;
    NEW.create_date := NOW();
    NEW.update_user := current_user;
    NEW.update_date := NOW();
    RETURN NEW;
END;

$$ LANGUAGE plpgsql;