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

  A task is finished when the history of the HEAD of the main checkout, any
  parent included, has its finished record (the merge of sf finish), whatever
  branch still has swarm/<id>. sf ls and sf next skip a finished task; sf
  start and every command that writes a record refuse it with "task <id> is
  finished" and exit status 1. sf reads only local branches: a remote
  swarm/<id> that could not be deleted never lists or reopens a task. A task
  that was squash-merged while its branch remains is not finished; it stays
  at its stage until sf finish merges it.

  sf handoff --to <role> names where the task goes: its stage or a later role
  on its route, or done when the stage is the route's last role. Any other
  --to exits 1 with "sf: --to <role> is not on route '<route>' at or after
  stage:<stage>", or for done "sf: --to done skips the rest of route
  '<route>' after stage:<stage>; hand off to the next role", and records
  nothing. Only sf route, the operator's, changes which roles a task visits.

  Background:
    Given a project with swarmforge initialized
    And task "A" titled "Add login" was created on route "specifier,coder"
    And task "B" titled "Fix bug" was started on route "coder,qa"

  # sf task state 1
  Scenario Outline: sf task state 1: sf ls prints the state of each task branch
    When I run "sf ls <options>"
    Then the exit status is 0
    And the output is "<output>"

    Examples:
      | options       | output                                                                      |
      |               | $A\tspecifier\t\tspecifier,coder\tAdd login\n$B\tcoder\t\tcoder,qa\tFix bug |
      | --stage coder | $B\tcoder\t\tcoder,qa\tFix bug                                              |

  # sf task state 2
  Scenario Outline: sf task state 2: sf ls shows a task that needs the operator
    Given I ran "sf ask $B Which"
    When I run "sf ls <options>"
    Then the exit status is 0
    And the output is "<output>"

    Examples:
      | options | output                                                                               |
      |         | $A\tspecifier\t\tspecifier,coder\tAdd login\n$B\tcoder\toperator\tcoder,qa\tFix bug |
      | --needs | $B\tcoder\toperator\tcoder,qa\tFix bug                                              |

  # sf task state 3
  Scenario: sf task state 3: sf ls --needs shows a task that waits for approval
    Given I ran "sf review $A"
    When I run "sf ls --needs"
    Then the exit status is 0
    And the output is "$A\tspecifier\tapproval\tspecifier,coder\tAdd login"

  # sf task state 4
  Scenario: sf task state 4: sf next lists the tasks at the stage that need nothing
    When I run "sf next coder"
    Then the exit status is 0
    And the output is "$B"

  # sf task state 5
  Scenario: sf task state 5: sf next skips a task that needs the operator
    Given I ran "sf ask $B Which"
    When I run "sf next coder"
    Then the exit status is 0
    And the output is "NO_TASK"

  # sf task state 6
  Scenario: sf task state 6: a finished task is not listed
    Given task "B" was finished
    When I run "sf ls"
    Then the exit status is 0
    And the output is "$A\tspecifier\t\tspecifier,coder\tAdd login"

  # sf task state 7
  Scenario Outline: sf task state 7: a squash-merged task whose branch remains is still listed at done
    Given task "B" was handed off to done and squash-merged into main
    When I run "<command>"
    Then the exit status is 0
    And the output is "<output>"

    Examples:
      | command       | output                                                                 |
      | sf ls         | $A\tspecifier\t\tspecifier,coder\tAdd login\n$B\tdone\t\tcoder,qa\tFix bug |
      | sf next coder | NO_TASK                                                                |

  # sf task state 8
  Scenario Outline: sf task state 8: a command that writes a record refuses a finished task
    Given task "B" was finished
    When I run "<command>"
    Then the exit status is 1
    And the error output has the line "sf: task $B is finished"
    And the branch "swarm/$B" does not exist

    Examples:
      | command           |
      | sf start $B       |
      | sf route $B coder |
      | sf handoff $B     |

  # sf task state 9
  Scenario: sf task state 9: sf ask refuses a finished task
    Given task "B" was finished
    When I run "sf ask $B Which"
    Then the exit status is 1
    And the error output has the line "sf: task $B is finished"
    And the branch "swarm/$B" does not exist

  # sf task state 10
  Scenario: sf task state 10: sf answer refuses a finished task
    Given task "B" was finished
    When I run "sf answer $B yes"
    Then the exit status is 1
    And the error output has the line "sf: task $B is finished"
    And the branch "swarm/$B" does not exist

  # sf task state 11
  Scenario Outline: sf task state 11: sf handoff --to stays on the rest of the route
    When I run "sf handoff $B --to <to>"
    Then the exit status is 1
    And the error output has the line "<error>"
    And the branch of task "B" did not move

    Examples:
      | to        | error                                                                                         |
      | done      | sf: --to done skips the rest of route 'coder,qa' after stage:coder; hand off to the next role |
      | cleaner   | sf: --to cleaner is not on route 'coder,qa' at or after stage:coder                           |
      | specifier | sf: --to specifier is not on route 'coder,qa' at or after stage:coder                         |
