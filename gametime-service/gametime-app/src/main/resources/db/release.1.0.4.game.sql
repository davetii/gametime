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

-- changeset 1.04.2 failOnError:true splitStatements:true

-- §3.6 (decisions.md #024 B): persist the RNG seed the engine rolled for this
-- game so the record is reproducible. Optional on input (random default), always
-- written by GameSimulator at persist time, echoed back on the Game header.
-- Nullable — plain column add, no Postgres-specific syntax, so no dbms gate.
alter table gametime.game
    add column seed BIGINT;

-- changeset 1.04.3 failOnError:true splitStatements:true

-- §3.10 (decisions.md #028 D): which team COMMITTED this event. Populated on
-- every FOUL event (SHOOTING_FOUL = the defender's team; the two-sided
-- REBOUNDING_FOUL_* = whichever side the roll picked), null elsewhere. Needed
-- because a rebounding foul can be committed by the OFFENSE (over-the-back), so
-- the committer is no longer implied by defense_team_id. Day-one consumer: the
-- derived penalty/bonus predicate (count FOUL events by committing team + period
-- >= BONUS_FOULS_PER_PERIOD — #028 A1, no stored counter). Nullable — plain
-- column add, no Postgres-specific syntax, so no dbms gate.
alter table gametime.game_event
    add column committing_team_id VARCHAR;

alter table gametime.game_event
    add constraint fk_game_event_committing_team
    foreign key (committing_team_id)
    REFERENCES gametime.team (id);

-- changeset 1.04.4 failOnError:true splitStatements:true

-- §3.14a (decisions.md #032 E, surfaced by #033): technical fouls, counted
-- SEPARATELY from the `fouls` column above. A technical does not count toward the
-- six-foul disqualification, so PlayerGameState keeps two counters and `fouls`
-- keeps meaning exactly "personal fouls" for isFouledOut(), foulTroubleLevel()
-- and the §3.10 penalty derivation. Merging them would have silently moved two
-- §3.13-calibrated numbers (foul-outs ~0.39 and the 4/5/6 distribution).
--
-- Surfaced here for PARITY (#033): the other eleven per-player accumulators on
-- PlayerGameState are all already persisted on this table, so leaving this one out
-- makes the box score inconsistent rather than lean. The information was already
-- queryable from the event log (FOUL events with outcome = 'TECHNICAL_FOUL'), so
-- this column is a denormalized convenience on the end-of-game snapshot, not a new
-- fact — the events stay the source of truth (#020).
--
-- Nullable to match every other stat column on this table (the entity uses Integer,
-- and EntityMapper/GameSimulator null-guard accordingly). Plain column add, no
-- Postgres-specific syntax, so no dbms gate.
alter table gametime.box_score
    add column technical_fouls SMALLINT;

-- changeset 1.04.1-triggers failOnError:true splitStatements:true dbms:postgresql

CREATE TRIGGER on_new_row_game BEFORE INSERT ON gametime.game FOR EACH ROW EXECUTE FUNCTION gametime.on_new_row();
CREATE TRIGGER on_new_row_game_event BEFORE INSERT ON gametime.game_event FOR EACH ROW EXECUTE FUNCTION gametime.on_new_row();
CREATE TRIGGER on_new_row_box_score BEFORE INSERT ON gametime.box_score FOR EACH ROW EXECUTE FUNCTION gametime.on_new_row();
