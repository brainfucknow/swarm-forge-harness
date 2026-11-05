Feature: sf task request

  sf new makes the branch swarm/<id> at the HEAD of the main checkout and adds
  one commit to it that changes no file. That commit is the task's created
  record and holds the task: its message is "sf created: <id>", a blank line,
  the title, a blank line, the request, a blank line, and the trailers
  SwarmForge-Task, SwarmForge-Record, SwarmForge-From, SwarmForge-Stage,
  SwarmForge-Route and SwarmForge-Needs. The stage is the first role of the
  route. Without -m the request is the title. Each run of whitespace in the
  title, line breaks included, becomes one space. The main checkout gets no
  commit and no file.

  sf start, sf show and sf ls read the title and the request back from that
  commit. A task created before this change has a created commit that adds
  one task file; sf reads the title and the request from that file as the
  created commit has it.

  Background:
    Given a project with swarmforge initialized

  # sf task request 1
  Scenario Outline: sf task request 1: sf new keeps the title and the request in the created record commit
    When I run sf new with the title "Add login", the request "<request>" and the route "<route>"
    Then the exit status is 0
    And the output matches "^add-login-[0-9a-f]{7}$"
    And the tip commit of task NEW changes no file
    And the parent of the tip commit of task NEW is the HEAD of the main checkout
    And the tip commit of task NEW has the message "sf created: NEW\n\nAdd login\n\n<request>\n\nSwarmForge-Task: NEW\nSwarmForge-Record: created\nSwarmForge-From: operator\nSwarmForge-Stage: <stage>\nSwarmForge-Route: <route>\nSwarmForge-Needs: none"
    And the main checkout has no file "tasks/NEW.md" and no uncommitted change

    Examples:
      | request                      | route              | stage     |
      | Users log in.                | specifier,coder,qa | specifier |
      | Users log in.\n\nWith email. | coder,qa           | coder     |

  # sf task request 2
  Scenario Outline: sf task request 2: the author of a record is git's identity
    Given the git identity of the project is "<identity>"
    When I run sf new with the title "Add login", the request "x" and the route "coder"
    Then the exit status is 0
    And the tip commit of task NEW has the author "<author>"

    Examples:
      | identity                    | author                      |
      | tester <tester@example.com> | tester <tester@example.com> |

  # sf task request 3
  Scenario: sf task request 3: the author of a record is the SwarmForge user when git has no identity
    Given the git identity of the project is "-"
    When I run sf new with the title "Add login", the request "x" and the route "coder"
    Then the exit status is 0
    And the tip commit of task NEW has the author "operator <operator@swarmforge.local>"

  # sf task request 4
  Scenario: sf task request 4: sf show prints the title and the request from the created record
    Given task "A" titled "Add login" with the request "Users log in.\n\nWith email." was created on route "coder"
    When I run "sf show $A"
    Then the exit status is 0
    And the output has the lines "TASK: $A\nTITLE: Add login\nSTATUS: open" before the line "---"
    And the output has the lines "---\nUsers log in.\n\nWith email.\n\ntype: created"
