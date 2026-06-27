Feature: Testing GameTime player API

  Scenario Outline: fetch team returns expected
    Given server is running for team test
    When fetchteam is called with "<id>"
    Then fetchteam api returns "<id>", "<coach>" , "<gm>" and "<conference>"
    Examples:
      | id  | coach            | gm            | conference  |
      | NY  | Frank Valcone    | JD Davison    | EAST        |

  Scenario Outline: fetch team returns expected http status
    Given server is running for team test
    When fetchteam is called with "<id>"
    Then fetchteam api returns http status code "<status>"
    Examples:
      | id 	| status|
      | NY	| 200   |
      | PHI	| 200   |
      | CONN| 200   |
      | BRK	| 200   |
      | WASH| 200   |
      | BOS	| 200   |
      | NC	| 200   |
      | ATL	| 200   |
      | JACK| 200   |
      | MIA	| 200   |
      | MI 	| 200   |
      | CHI	| 200   |
      | IND	| 200   |
      | MIN	| 200   |
      | CLEV| 200   |
      | TOR	| 200   |
      | BUF	| 200   |
      | VAN	| 200   |
      | MIL	| 200   |
      | PIT	| 200   |
      | STL	| 200   |
      | KC	| 200   |
      | TENN| 200   |
      | HOU	| 200   |
      | SA 	| 200   |
      | DAL	| 200   |
      | NOLA| 200   |
      | AL 	| 200   |
      | OKL	| 200   |
      | DEN	| 200   |
      | LA 	| 200   |
      | CA 	| 200   |
      | SD	| 200   |
      | SF	| 200   |
      | PHO	| 200   |
      | POR	| 200   |
      | SEA	| 200   |
      | UT	| 200   |
      | VAN	| 200   |
      | LV	| 200   |
