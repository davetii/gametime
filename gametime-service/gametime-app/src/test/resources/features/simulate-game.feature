Feature: §3.6 Simulation APIs — simulate, fetch, and play-by-play

  Scenario: simulate a game returns a FINAL GameResult with both box scores
    Given server is running for game test
    When simulategame is called for "BOS" vs "LA"
    Then game api returns http status code "200"
    And the game result is FINAL with both box scores populated

  Scenario: a same-team matchup is rejected with 422
    Given server is running for game test
    When simulategame is called for "BOS" vs "BOS"
    Then game api returns http status code "422"

  Scenario: an unknown team on simulate is rejected with 404
    Given server is running for game test
    When simulategame is called for "NOPE" vs "LA"
    Then game api returns http status code "404"

  Scenario: a simulated game is re-fetchable by id
    Given server is running for game test
    When simulategame is called for "CHI" vs "NY"
    And fetchgame is called for the last simulated game
    Then game api returns http status code "200"
    And the game result is FINAL with both box scores populated

  Scenario: fetching an unknown game id returns 404
    Given server is running for game test
    When fetchgame is called for "does-not-exist"
    Then game api returns http status code "404"

  Scenario: play-by-play returns an ordered event list for a simulated game
    Given server is running for game test
    When simulategame is called for "MIA" vs "PHI"
    And playbyplay is called for the last simulated game
    Then game api returns http status code "200"
    And the play-by-play is non-empty and sequence-ordered

  Scenario: play-by-play for an unknown game id returns 404
    Given server is running for game test
    When playbyplay is called for "does-not-exist"
    Then game api returns http status code "404"
