Feature: Testing GameTime player API

  Scenario Outline: fetch team returns expected
    When fetchteam is called with "<id>"
    Then fetchteam api returns "<id>", "<coach>" , "<gm>" and "<conference>"
    Examples:
      | id  | coach            | gm            | conference  |
      | NY  | Frank Valcone    | JD Davison    | EAST        |

  Scenario Outline: fetch team returns expected http status
    When fetchteam is called with "<id>"
    Then fetchteam api returns http status code "<status>"
    Examples:
      | id 	| status  |
      | NY	| 200 OK  |
      | PHI	| 200 OK  |
      | CONN| 200 OK  |
      | BRK	| 200 OK  |
      | WASH| 200 OK  |
      | BOS	| 200 OK  |
      | NC	| 200 OK  |
      | ATL	| 200 OK  |
      | JACK| 200 OK  |
      | MIA	| 200 OK  |
      | MI 	| 200 OK  |
      | CHI	| 200 OK  |
      | IND	| 200 OK  |
      | MIN	| 200 OK  |
      | CLEV| 200 OK  |
      | TOR	| 200 OK  |
      | BUF	| 200 OK  |
      | VAN	| 200 OK  |
      | MIL	| 200 OK  |
      | PIT	| 200 OK  |
      | STL	| 200 OK  |
      | KC	| 200 OK  |
      | TENN| 200 OK  |
      | HOU	| 200 OK  |
      | SA 	| 200 OK  |
      | DAL	| 200 OK  |
      | NOLA| 200 OK  |
      | AL 	| 200 OK  |
      | OKL	| 200 OK  |
      | DEN	| 200 OK  |
      | LA 	| 200 OK  |
      | CA 	| 200 OK  |
      | SD	| 200 OK  |
      | SF	| 200 OK  |
      | PHO	| 200 OK  |
      | POR	| 200 OK  |
      | SEA	| 200 OK  |
      | UT	| 200 OK  |
      | VAN	| 200 OK  |
      | LV	| 200 OK  |
