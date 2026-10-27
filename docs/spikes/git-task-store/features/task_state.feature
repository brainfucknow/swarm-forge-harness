Feature: sf task state

  The state of a task is the SwarmForge-Stage, SwarmForge-Route and
  SwarmForge-Needs trailers of its newest record: the newest commit with
  "SwarmForge-Task: <id>" in the first-parent history of its branch. sf ls,
  sf next and sf stage read it from the branches; there is no other store.

  sf ls prints one line per task that is not finished, sorted by id: id,
  stage, needs (empty when none), route and title, separated by tabs. --stage
  <role> keeps the tasks at that stage, --needs keeps the tasks that need the
  operator or an approval. sf next <role> prints the ids of the tasks at that
  stage that need nothing, or NO_TASK.

  A task is a local branch swarm/<id>, or a remote-tracking branch
  <remote>/swarm/<id> when there is no local one. A remote-tracking task
  branch whose tip the HEAD of the main checkout contains is a finished task
  and is not listed. sf start of a task that has only a remote-tracking
  branch makes the local branch at its tip.

  Background:
    Given a project with swarmforge initialized and a remote "origin"
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
  Scenario: sf task state 4: a task that only another clone made is listed and can be started
    Given another clone of "origin" created task "C" titled "Port tool" on route "qa" and pushed its branch
    And I ran "git fetch origin"
    When I run "sf start $C"
    Then the exit status is 0
    And the output has the line "STAGE: qa"
    And the branch "swarm/$C" is at the tip of "origin/swarm/$C"

  # sf task state 5
  Scenario: sf task state 5: sf ls lists a remote-only task, and a local branch wins over its remote-tracking branch
    Given another clone of "origin" created task "C" titled "Port tool" on route "qa" and pushed its branch
    And I ran "git push origin swarm/$A"
    And I ran "sf ask $A Which"
    And I ran "git fetch origin"
    When I run "sf ls"
    Then the exit status is 0
    And the output is "$A\tspecifier\toperator\tspecifier,coder\tAdd login\n$B\tcoder\t\tcoder,qa\tFix bug\n$C\tqa\t\tqa\tPort tool"

  # sf task state 6
  Scenario: sf task state 6: a remote-tracking task branch that the main checkout contains is not listed
    Given I ran "git push origin swarm/$B"
    And task "B" was finished
    And I ran "git fetch origin"
    When I run "sf ls"
    Then the exit status is 0
    And the output is "$A\tspecifier\t\tspecifier,coder\tAdd login"
