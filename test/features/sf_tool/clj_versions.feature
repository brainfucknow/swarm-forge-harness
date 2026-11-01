Feature: sf tool clj versions

  A wrapper for a tool of kind clj depends on the Maven version in the ref
  column of its row, never on RELEASE or LATEST, and sf tool ensure prints
  "INSTALLED: <tool> <wrapper> @<version>". The cloverage wrapper also
  depends on speclj, at the version the speclj row pins. The pins of the
  crap4clj coverage config (crap4clj_coverage.feature) are not read from
  tools.conf.

  Background:
    Given a project with swarmforge initialized
    And the main checkout's tools.conf pins "cloverage" at "9.8.7"
    And the main checkout's tools.conf pins "speclj" at "6.5.4"

  # sf tool clj versions 1
  Scenario Outline: sf tool clj versions 1: a clj wrapper uses the pinned versions
    When I run "sf tool ensure <tool>" in the main checkout
    Then the exit status is 0
    And the output has the line "INSTALLED: <tool> MAIN/.swarmforge/bin/<tool> @<version>"
    And the file "MAIN/.swarmforge/bin/<tool>" mentions "<dependency>"
    And the file "MAIN/.swarmforge/bin/<tool>" does not mention "RELEASE"

    Examples:
      | tool      | version | dependency                                 |
      | cloverage | 9.8.7   | cloverage/cloverage {:mvn/version "9.8.7"} |
      | cloverage | 9.8.7   | speclj/speclj {:mvn/version "6.5.4"}       |
      | speclj    | 6.5.4   | speclj/speclj {:mvn/version "6.5.4"}       |
