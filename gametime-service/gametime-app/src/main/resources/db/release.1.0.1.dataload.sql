-- liquibase formatted sql

-- changeset dave:1.01.2 failOnError:true splitStatements:false

-- teams
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'NY', 'New York','Fastbacks', '2', '2', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'PHI', 'Philadelphia','Flames', '3', '3', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'CONN', 'Connecticut','Huskies', '4', '4', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'BRK', 'Brooklyn ','Knights', '5', '5', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'WASH', 'Washington','Chiefs', '6', '6', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'BOS', 'Boston','Steeds', '7', '7', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'NC', 'North Carolina','Tarheels', '8', '8', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'ATL', 'Atlanta','Ravens', '9', '9', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'JACK', 'Jacksonville','Gators', '10', '10', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'MIA', 'Miami','Hurricane', '11', '11', 'EAST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'MI', 'Michigan','Panthers', '12', '12', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'CHI', 'Chicago','Blackhawks', '13', '13', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'IND', 'Indiana','Hoosiers', '14', '14', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'MIN', 'Minnesota','Timberwolves', '15', '15', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'CLEV', 'Cleveland','Indians', '16', '16', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'TOR', 'Toronto','Blazers', '17', '17', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'BUF', 'Buffalo','Sabres', '18', '18', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'VA', 'Virginia','Minutemen', '19', '19', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'MIL', 'Milwaukee','Jets', '20', '20', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'PIT', 'Pittsburgh','Bears', '21', '21', 'NORTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'STL', 'Saint Louis','Devils', '22', '22', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'KC', 'Kansas City','JayHawks', '23', '23', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'TENN', 'Tennessee','RunninRebels', '24', '24', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'HOU', 'Houston','Wildcats', '25', '25', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'SA', 'San Antonio','Regulators', '26', '26', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'DAL', 'Dallas','Bulls', '27', '27', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'NOLA', 'New Orleans','Jazz', '28', '28', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'AL', 'Alabama','Raiders', '29', '29', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'OKL', 'Oklahoma','Sooners', '30', '30', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'DEN', 'Denver','Rush', '31', '31', 'SOUTH');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'LA', 'Los Angeles','Kings', '32', '32', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'CA', 'California','Thunder', '33', '33', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'SD', 'San Diego','Suns', '34', '34', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'SF', 'San Fransisco','Bruins', '35', '35', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'PHO', 'Phoenix','Rockets', '36', '36', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'POR', 'Portland','Cougars', '37', '37', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'SEA', 'Seattle','Stars', '38', '38', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'UT', 'Utah','Aggies', '39', '39', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'VAN', 'Vancouver','Badgers', '40', '40', 'WEST');
insert into gametime.team(id, locale, name, coach_id, gm_id, conference) values ( 'LV', 'Las Vegas','Gamblers', '41', '41', 'WEST');

