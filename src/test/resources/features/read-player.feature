Feature: Testing GameTime player API

  Scenario Outline: read player returns expected
    Given server is running for player test
    When readplayer is called with "<id>"
    Then readplayer api returns "<thing>" , "<firstname>" , "<lastname>" and "<position>"
    Examples:
      | id                                    | thing    | firstname | lastname    | position  |
      | 330eb324-3382-4fca-b20e-2b4c7e278047  | ROTATION  | Adrian    | Mcpherson   | F         |
      | 0e686b5b-5ec9-43e3-ad7f-f32bc38f1818  | BENCH     | Keith     | Holdsworth  | FC        |

  Scenario Outline: read player returns expected http status
    Given server is running for player test
    When readplayer is called with "<id>"
    Then readplayer api returns http status code "<status>"
    Examples:
      | id                                    | status|
      | 330eb324-3382-4fca-b20e-2b4c7e278047  | 200   |
      | aaa                                   | 404   |