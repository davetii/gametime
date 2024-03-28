Feature: Testing GameTime player API

  Scenario Outline: fetch team returns expected
    When fetchteam is called with "<id>"
    Then fetchteam api returns "<id>", "<coach>" , "<gm>" and "<conference>"
    Examples:
      | id  | coach            | gm            | conference  |
      | NY  | Frank Valcone    | JD Davison    | EAST        |



