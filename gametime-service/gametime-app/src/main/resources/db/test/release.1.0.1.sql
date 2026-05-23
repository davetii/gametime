-- liquibase formatted sql

-- changeset 1.01.1 failOnError:true splitStatements:true

CREATE TABLE gm 
(
	id VARCHAR primary key, 
	first_name VARCHAR not null, 
	last_name VARCHAR not null,
    create_user VARCHAR not null,
    create_date timestamp not null,
    update_user VARCHAR not null,
    update_date timestamp not null
);

CREATE TRIGGER on_new_row_gm BEFORE INSERT ON gm FOR EACH ROW EXECUTE FUNCTION on_new_row();

CREATE TABLE coach 
(
	id VARCHAR primary key, 
	first_name VARCHAR not null, 
	last_name VARCHAR not null,
    create_user VARCHAR not null,
    create_date timestamp not null,
    update_user VARCHAR not null,
    update_date timestamp not null
);

CREATE TRIGGER on_new_row_coach BEFORE INSERT ON coach FOR EACH ROW EXECUTE FUNCTION on_new_row();

CREATE TABLE team 
(
	id VARCHAR primary key, 
	locale VARCHAR not null, 
	name VARCHAR not null, 
	coach_id VARCHAR not null,
	gm_id VARCHAR not null,
	conference VARCHAR not null,
    create_user VARCHAR not null,
    create_date timestamp not null,
    update_user VARCHAR not null,
    update_date timestamp not null
);

alter table team
    add constraint fk_team_coach
    foreign key (coach_id) 
    REFERENCES coach (id);

alter table team  
    add constraint fk_team_gm
    foreign key (gm_id) 
    REFERENCES gm (id);

CREATE TRIGGER on_new_row_team BEFORE INSERT ON team FOR EACH ROW EXECUTE FUNCTION on_new_row();

CREATE TABLE player 
(
	id VARCHAR primary key, 
	first_name VARCHAR not null, 
	last_name VARCHAR not null, 
	team_id VARCHAR not null,
	draft_slot VARCHAR (10), 
	years_pro smallint, 
	height  VARCHAR (10), 
	weight  VARCHAR (10), 
	status  VARCHAR (10) not null, 
	position  VARCHAR (5) not null, 
	size SMALLINT, 
	strength SMALLINT, 
	intelligence SMALLINT, 
	shot_skill SMALLINT, 
	shot_selection SMALLINT, 
	endurance SMALLINT, 
	agility SMALLINT, 
	handle SMALLINT, 
	speed SMALLINT, 
	energy SMALLINT, 
	health SMALLINT, 
	determination SMALLINT, 
	luck SMALLINT, 
	charisma SMALLINT, 
	ego SMALLINT, 
	cohesion SMALLINT,
    create_user VARCHAR not null,
    create_date timestamp not null,
    update_user VARCHAR not null,
    update_date timestamp not null
);

alter table player  
    add constraint fk_player_team
    foreign key (team_id) 
    REFERENCES team (id);

CREATE TRIGGER on_new_row_player BEFORE INSERT ON player FOR EACH ROW EXECUTE FUNCTION on_new_row();
