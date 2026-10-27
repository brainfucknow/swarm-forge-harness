Feature: sf remote tasks

  A task is a local branch swarm/<id>, or a remote-tracking branch
  <remote>/swarm/<id> when there is no local one. sf considers one remote:
  SWARMFORGE_REMOTE when it is set, else origin; other remotes are ignored.
  A local branch wins over its remote-tracking branch. sf start of a task
  that has only a remote-tracking branch makes the local branch at its tip.
  A finished task (tasks/done/<id>.md in the HEAD of the main checkout) is
  not listed, whatever remote-tracking branch still has swarm/<id>.

  Background:
    Given a project with swarmforge initialized and a remote "origin"
    And task "A" titled "Add login" was created on route "specifier,coder"
    And task "B" titled "Fix bug" was started on route "coder,qa"

  # sf remote tasks 1
  Scenario: sf remote tasks 1: a task that only another clone made is listed and can be started
    Given another clone of "origin" created task "C" titled "Port tool" on route "qa" and pushed its branch
    And I ran "git fetch origin"
    When I run "sf start $C"
    Then the exit status is 0
    And the output has the line "STAGE: qa"
    And the branch "swarm/$C" is at the tip of "origin/swarm/$C"

  # sf remote tasks 2
  Scenario: sf remote tasks 2: sf ls lists a remote-only task, and a local branch wins over its remote-tracking branch
    Given another clone of "origin" created task "C" titled "Port tool" on route "qa" and pushed its branch
    And I ran "git push origin swarm/$A"
    And I ran "sf ask $A Which"
    And I ran "git fetch origin"
    When I run "sf ls"
    Then the exit status is 0
    And the output is "$A\tspecifier\toperator\tspecifier,coder\tAdd login\n$B\tcoder\t\tcoder,qa\tFix bug\n$C\tqa\t\tqa\tPort tool"

  # sf remote tasks 3
  Scenario Outline: sf remote tasks 3: a remote-tracking branch of a finished task is not listed
    Given I ran "git push origin swarm/$B"
    And task "B" <finish>
    And I ran "git fetch origin"
    When I run "<command>"
    Then the exit status is 0
    And the output is "<output>"

    Examples:
      | finish                                              | command       | output                                      |
      | was finished                                        | sf ls         | $A\tspecifier\t\tspecifier,coder\tAdd login |
      | was handed off to done and squash-merged into main  | sf ls         | $A\tspecifier\t\tspecifier,coder\tAdd login |
      | was handed off to done and squash-merged into main  | sf next coder | NO_TASK                                     |

  # sf remote tasks 4
  Scenario Outline: sf remote tasks 4: sf considers only one remote, SWARMFORGE_REMOTE or else origin
    Given a second remote "upstream"
    And another clone of "upstream" created task "C" titled "Port tool" on route "qa" and pushed its branch
    And I ran "git fetch upstream"
    When I run "sf ls" with SWARMFORGE_REMOTE "<remote>"
    Then the exit status is 0
    And the output is "<output>"

    Examples:
      | remote   | output                                                                                                         |
      | -        | $A\tspecifier\t\tspecifier,coder\tAdd login\n$B\tcoder\t\tcoder,qa\tFix bug                                    |
      | upstream | $A\tspecifier\t\tspecifier,coder\tAdd login\n$B\tcoder\t\tcoder,qa\tFix bug\n$C\tqa\t\tqa\tPort tool          |
