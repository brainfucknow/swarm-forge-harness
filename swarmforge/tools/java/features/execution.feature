Feature: sf-java execution

  sf-java supports a single Maven module with pom.xml, src/main/java and
  src/test/java, Java 17 or later, and JUnit 5. Tests run through a dedicated
  runner; Maven may prepare dependencies and compile classes, with test
  execution disabled. JUnit tests tagged property run only via property.
  Commands succeed with 0, fail tests or quality findings with 1, and report
  usage, missing prerequisites or invalid inputs with 2 and an sf-java:
  diagnostic on stderr. No project pom.xml edit is implicit.

  Background:
    Given a single-module Maven Java project in a task worktree
    And controllable Java, Maven and JUnit tools on PATH

  # sf-java execution 1
  Scenario Outline: sf-java execution 1: normal and property tests stay separate
    Given the project has a passing ordinary JUnit test covering production code
    And the project has a passing JUnit test tagged property
    When I run sf-java with "<command>"
    Then the exit status is 0
    And the dedicated runner executes "<selected>" tests
    And Maven does not execute tests

    Examples:
      | command  | selected |
      | test     | ordinary |
      | property | property |
      | coverage | ordinary |

  # sf-java execution 2
  Scenario Outline: sf-java execution 2: test failures fail the command
    Given an ordinary JUnit test fails
    When I run sf-java with "<command>"
    Then the exit status is 1
    And the output identifies the failing test

    Examples:
      | command  |
      | test     |
      | coverage |

  # sf-java execution 3
  Scenario: sf-java execution 3: compilation failure prevents the runner
    Given the Java sources fail compilation
    When I run sf-java with "test"
    Then the exit status is 1
    And the dedicated runner was not started

  # sf-java execution 4
  Scenario Outline: sf-java execution 4: unsupported inputs fail clearly
    Given the project has "<problem>"
    When I run sf-java with "test"
    Then the exit status is 2
    And the error output identifies "<problem>"
    And the dedicated runner was not started

    Examples:
      | problem              |
      | no pom.xml           |
      | Java older than 17   |
      | no Maven executable  |
      | a Maven reactor root |

  # sf-java execution 5
  Scenario Outline: sf-java execution 5: stale coverage cannot make a run pass
    Given a stale JaCoCo XML report is <state>
    And a successful dedicated coverage run writes no new JaCoCo XML report
    When I run sf-java with "coverage"
    Then the exit status is 2
    And the error output identifies the missing new coverage report

    Examples:
      | state   |
      | present |
      | absent  |

  # sf-java execution 6
  Scenario: sf-java execution 6: coverage produces a usable JaCoCo report
    Given the project has a passing ordinary JUnit test covering production code
    When I run sf-java with "coverage"
    Then the exit status is 0
    And a new JaCoCo XML report records the covered production code
    And the output identifies the coverage report path
