-- liquibase formatted sql

-- changeset 1.04.1 failOnError:true splitStatements:true

-- A single game: matchup + outcome. References home/away team only — there is
-- no season/schedule table yet (Phase 5), so no season FK (decisions.md #020).
CREATE TABLE gametime.game
(
    id VARCHAR primary key,
    home_team_id VARCHAR not null,
    away_team_id VARCHAR not null,
    status VARCHAR (15) not null,
    -- period scores + final score (regulation + overtime captured as period rows
    -- on game_event; these are the summary totals)
    home_score SMALLINT,
    away_score SMALLINT,
    periods SMALLINT,
    create_user VARCHAR default 'system' not null,
    create_date timestamp default CURRENT_TIMESTAMP not null,
    update_user VARCHAR default 'system' not null,
    update_date timestamp default CURRENT_TIMESTAMP not null
);

alter table gametime.game
    add constraint fk_game_home_team
    foreign key (home_team_id)
    REFERENCES gametime.team (id);

alter table gametime.game
    add constraint fk_game_away_team
    foreign key (away_team_id)
    REFERENCES gametime.team (id);

-- Possession-by-possession event log. Every event is persisted (decisions.md
-- #020). `sequence` is monotonic across the WHOLE game and does not restart per
-- period, so play-by-play is a single ORDER BY sequence read. Shape is minimal
-- and grows additively with the §3.2 engine.
CREATE TABLE gametime.game_event
(
    id VARCHAR primary key,
    game_id VARCHAR not null,
    sequence INTEGER not null,
    period SMALLINT not null,
    offense_team_id VARCHAR not null,
    defense_team_id VARCHAR not null,
    play_type VARCHAR (15) not null,
    outcome VARCHAR,
    primary_player_id VARCHAR,
    -- Optional assister on a made-FG SHOT event (§3.4 ball movement). Nullable —
    -- not every made shot is assisted, and non-SHOT events never carry one.
    -- BoxScore.assists reconciles against the count of SHOT events with this set.
    assist_player_id VARCHAR,
    create_user VARCHAR default 'system' not null,
    create_date timestamp default CURRENT_TIMESTAMP not null,
    update_user VARCHAR default 'system' not null,
    update_date timestamp default CURRENT_TIMESTAMP not null
);

alter table gametime.game_event
    add constraint fk_game_event_game
    foreign key (game_id)
    REFERENCES gametime.game (id);

alter table gametime.game_event
    add constraint fk_game_event_player
    foreign key (primary_player_id)
    REFERENCES gametime.player (id);

alter table gametime.game_event
    add constraint fk_game_event_assist_player
    foreign key (assist_player_id)
    REFERENCES gametime.player (id);

-- Per-player stat line for a single game, one row per (game, player). No
-- team_id: the player's team is derivable from game + player_team, so storing
-- it here would duplicate that fact (decisions.md #020, cf. #013/#015).
CREATE TABLE gametime.box_score
(
    id VARCHAR primary key,
    game_id VARCHAR not null,
    player_id VARCHAR not null,
    points SMALLINT,
    offensive_rebounds SMALLINT,
    defensive_rebounds SMALLINT,
    assists SMALLINT,
    steals SMALLINT,
    blocks SMALLINT,
    turnovers SMALLINT,
    fouls SMALLINT,
    minutes SMALLINT,
    field_goals_attempted SMALLINT,
    field_goals_made SMALLINT,
    three_pointers_attempted SMALLINT,
    three_pointers_made SMALLINT,
    free_throws_attempted SMALLINT,
    free_throws_made SMALLINT,
    create_user VARCHAR default 'system' not null,
    create_date timestamp default CURRENT_TIMESTAMP not null,
    update_user VARCHAR default 'system' not null,
    update_date timestamp default CURRENT_TIMESTAMP not null
);

alter table gametime.box_score
    add constraint fk_box_score_game
    foreign key (game_id)
    REFERENCES gametime.game (id);

alter table gametime.box_score
    add constraint fk_box_score_player
    foreign key (player_id)
    REFERENCES gametime.player (id);

-- changeset 1.04.1-triggers failOnError:true splitStatements:true dbms:postgresql

CREATE TRIGGER on_new_row_game BEFORE INSERT ON gametime.game FOR EACH ROW EXECUTE FUNCTION gametime.on_new_row();
CREATE TRIGGER on_new_row_game_event BEFORE INSERT ON gametime.game_event FOR EACH ROW EXECUTE FUNCTION gametime.on_new_row();
CREATE TRIGGER on_new_row_box_score BEFORE INSERT ON gametime.box_score FOR EACH ROW EXECUTE FUNCTION gametime.on_new_row();
