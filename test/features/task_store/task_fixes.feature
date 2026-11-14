Feature: sf task fixes

  sf new takes --fixes <target> and --found-by <detector>. The target names
  what the new task repairs: a task, given as a full id, a hex part or a
  prefix, open or finished, or a pull request, given as # and a number that
  does not start with 0. sf does not check that the pull request exists. The
  detector says how the defect was found: review, ci or operator.

  The created record keeps them in the trailers SwarmForge-Fixes and
  SwarmForge-Found-By, after SwarmForge-Needs. SwarmForge-Fixes holds the
  full task id, or the pull request as given. sf show and sf start print
  them in the created block as "fixes:" and "found-by:" after "route:". sf
  harvest prints them on every line as the keys "fixes" and "found_by",
  null when the task has none. A task created without the options has
  neither trailer and neither block line.

  sf new refuses a task it cannot resolve, any other target form, a
  detector outside the three, either option given twice, and --found-by
  without --fixes. A refused sf new prints no id and makes no branch.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was created on route "coder"

  # sf task fixes 1
  Scenario Outline: sf task fixes 1: sf new keeps the target and the detector in the created record
    When I run sf new with the title "Fix login", the request "x", the route "coder" and the options "<options>"
    Then the exit status is 0
    And the tip commit of task NEW has the message "sf created: NEW\n\nFix login\n\nx\n\nSwarmForge-Task: NEW\nSwarmForge-Record: created\nSwarmForge-From: operator\nSwarmForge-Stage: coder\nSwarmForge-Route: coder\nSwarmForge-Needs: none\n<trailers>"

    Examples:
      | options                          | trailers                                               |
      | --fixes $A --found-by review     | SwarmForge-Fixes: $A\nSwarmForge-Found-By: review      |
      | --fixes $A:hex --found-by ci     | SwarmForge-Fixes: $A\nSwarmForge-Found-By: ci          |
      | --found-by operator --fixes add  | SwarmForge-Fixes: $A\nSwarmForge-Found-By: operator    |
      | --fixes #115 --found-by ci       | SwarmForge-Fixes: #115\nSwarmForge-Found-By: ci        |
      | --fixes #7                       | SwarmForge-Fixes: #7                                   |

  # sf task fixes 2
  Scenario: sf task fixes 2: a finished task can be the target
    Given task "A" was finished
    And I ran "sf new Fix -m x --route coder --fixes $A:hex"
    And task "B" is the task that command created
    When I run "sf show $B"
    Then the exit status is 0
    And the output has the line "fixes: $A"

  # sf task fixes 3
  Scenario Outline: sf task fixes 3: sf show and sf start print the target and the detector in the created block
    Given I ran "sf new Fix -m x --route coder <options>"
    And task "B" is the task that command created
    When I run "sf <command> $B"
    Then the exit status is 0
    And the output has the lines "type: created\nfrom: operator\nbranch: swarm/$B\ncommit: $B:created\nroute: coder\n<lines>"

    Examples:
      | command | options                      | lines                         |
      | show    | --fixes $A --found-by review | fixes: $A\nfound-by: review   |
      | start   | --fixes #115 --found-by ci   | fixes: #115\nfound-by: ci     |

  # sf task fixes 4
  Scenario: sf task fixes 4: sf show prints no detector line for a task without one
    Given I ran "sf new Fix -m x --route coder --fixes #7"
    And task "B" is the task that command created
    When I run "sf show $B"
    Then the output ends with the lines "route: coder\nfixes: #7"

  # sf task fixes 5
  Scenario Outline: sf task fixes 5: sf harvest prints the target and the detector of every task
    Given I ran "sf new Fix -m x --route coder --fixes <target> --found-by <detector>"
    And task "B" is the task that command created
    When I run "sf harvest"
    Then the exit status is 0
    And the output matches "\"id\":\"$B\"[^\n]*\"fixes\":\"<stored>\""
    And the output matches "\"id\":\"$B\"[^\n]*\"found_by\":\"<detector>\""
    And the output matches "\"id\":\"$A\"[^\n]*\"fixes\":null"
    And the output matches "\"id\":\"$A\"[^\n]*\"found_by\":null"

    Examples:
      | target | detector | stored |
      | $A:hex | review   | $A     |
      | #115   | operator | #115   |

  # sf task fixes 6
  Scenario Outline: sf task fixes 6: sf new refuses a target or a detector it cannot keep
    When I run "sf new Fix -m x --route coder <options>"
    Then the exit status is 1
    And the output is ""
    And the error output has the line "<refusal>"

    Examples:
      | options                               | refusal                                                     |
      | --fixes nosuch                        | sf: no task 'nosuch'                                        |
      | --fixes #0                            | sf: --fixes needs a task id or '#<number>', got '#0'        |
      | --fixes #12a                          | sf: --fixes needs a task id or '#<number>', got '#12a'      |
      | --fixes #                             | sf: --fixes needs a task id or '#<number>', got '#'         |
      | --fixes #7 --fixes #8                 | sf: --fixes given twice                                     |
      | --found-by review                     | sf: --found-by needs --fixes                                |
      | --fixes #7 --found-by lint            | sf: --found-by needs review, ci or operator, got 'lint'     |
      | --fixes #7 --found-by ci --found-by ci | sf: --found-by given twice                                 |
      | --fixes                               | sf: --fixes needs a value                                   |

  # sf task fixes 7
  Scenario: sf task fixes 7: sf new refuses a target that names two tasks
    Given task "B" titled "Add logout" was created on route "coder"
    When I run "sf new Fix -m x --route coder --fixes add-log"
    Then the exit status is 1
    And the error output has the line "sf: task id 'add-log' is ambiguous: $A $B"
