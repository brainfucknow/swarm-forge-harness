Feature: sf task state

  The state of a task is the SwarmForge-Stage, SwarmForge-Route and
  SwarmForge-Needs trailers of its newest record: the newest commit with
  "SwarmForge-Task: <id>" in the first-parent history of its branch. sf ls,
  sf next and sf stage read it from the local swarm/<id> branches; there is
  no other store.

  sf ls prints one line per task that is not finished, sorted by id: id,
  stage, needs (empty when none), route and title, separated by tabs. --stage
  <role> keeps the tasks at that stage, --needs keeps the tasks that need the
  operator or an approval. sf next <role> prints the ids of the tasks at that
  stage that need nothing, or NO_TASK.

  A task is finished when tasks/done/<id>.md is in the HEAD of the main
  checkout, whatever branch still has swarm/<id>, also after a squash merge.
  sf ls and sf next skip a finished task; sf start and every command that
  writes a record refuse it with "task <id> is finished" and exit status 1.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was created on route "specifier,coder"
    And task "B" titled "Fix bug" was started on route "coder,qa"

  # sf task state 1
  Scenario Outline: sf task state 1: sf ls prints the state of each task branch
    Given I ran "<setup>"
    When I run "sf ls <options>"
    Then the exit status is 0
    And the output is "<output>"

    Examples:
      | setup             | options       | output                                                                                    |
      | sf stage $A       |               | $A\tspecifier\t\tspecifier,coder\tAdd login\n$B\tcoder\t\tcoder,qa\tFix bug               |
      | sf ask $B Which   |               | $A\tspecifier\t\tspecifier,coder\tAdd login\n$B\tcoder\toperator\tcoder,qa\tFix bug       |
      | sf ask $B Which   | --needs       | $B\tcoder\toperator\tcoder,qa\tFix bug                                                    |
      | sf review $A      | --needs       | $A\tspecifier\tapproval\tspecifier,coder\tAdd login                                       |
      | sf stage $A       | --stage coder | $B\tcoder\t\tcoder,qa\tFix bug                                                            |

  # sf task state 2
  Scenario Outline: sf task state 2: sf next lists the tasks at the stage that need nothing
    Given I ran "<setup>"
    When I run "sf next coder"
    Then the exit status is 0
    And the output is "<output>"

    Examples:
      | setup             | output  |
      | sf stage $B       | $B      |
      | sf ask $B Which   | NO_TASK |

  # sf task state 3
  Scenario: sf task state 3: a finished task is not listed
    Given task "B" was finished
    When I run "sf ls"
    Then the exit status is 0
    And the output is "$A\tspecifier\t\tspecifier,coder\tAdd login"

  # sf task state 4
  Scenario Outline: sf task state 4: a squash-merged task is finished although its branch remains
    Given task "B" was handed off to done and squash-merged into main
    When I run "<command>"
    Then the exit status is 0
    And the output is "<output>"

    Examples:
      | command          | output                                      |
      | sf ls            | $A\tspecifier\t\tspecifier,coder\tAdd login |
      | sf next coder    | NO_TASK                                     |

  # sf task state 5
  Scenario Outline: sf task state 5: a command that writes a record refuses a finished task
    Given task "B" was handed off to done and squash-merged into main
    When I run "<command>"
    Then the exit status is 1
    And the error output has the line "sf: task $B is finished"
    And the branch of task "B" did not move

    Examples:
      | command                 |
      | sf start $B             |
      | sf ask $B Which         |
      | sf answer $B yes        |
      | sf route $B coder       |
      | sf handoff $B           |
