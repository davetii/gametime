Feature: Testing GameTime player API

  Scenario Outline: read player returns expected
    When readplayer is called with "<id>"
    Then api returns "<status>" , "<firstname>" , "<lastname>" and "<position>"
    Examples:
      | id                                    | status    | firstname | lastname  | position  |
      | 330eb324-3382-4fca-b20e-2b4c7e278047  | ROTATION  | Adrian    | Mcpherson | F         |
      

