Feature: sf task file

  sf new makes the branch swarm/<id> at the HEAD of the main checkout and adds
  one commit to it. That commit adds tasks/<id>.md and no other file: front
  matter with the id and the UTC date of creation, the title as a "#"
  heading, and the request. The commit is the task's created record. Its
  message is "sf created: <id>", a blank line, the title, a blank line, and
  the trailers SwarmForge-Task, SwarmForge-Record, SwarmForge-From,
  SwarmForge-Stage, SwarmForge-Route and SwarmForge-Needs. The stage is the
  first role of the route. The main checkout gets no commit and no file.

  Background:
    Given a project with swarmforge initialized

  # sf task file 1
  Scenario Outline: sf task file 1: sf new adds the task file in the created record commit
    When I run sf new with the title "Add login", the request "<request>" and the route "<route>"
    Then the exit status is 0
    And the output matches "^add-login-[0-9a-f]{7}$"
    And the file "tasks/NEW.md" on the branch of task NEW is "---\nid: NEW\ncreated: DATE\n---\n\n# Add login\n\n<body>"
    And the tip commit of task NEW adds only the file "tasks/NEW.md"
    And the parent of the tip commit of task NEW is the HEAD of the main checkout
    And the tip commit of task NEW has the message "sf created: NEW\n\nAdd login\n\nSwarmForge-Task: NEW\nSwarmForge-Record: created\nSwarmForge-From: operator\nSwarmForge-Stage: <stage>\nSwarmForge-Route: <route>\nSwarmForge-Needs: none"
    And the main checkout has no file "tasks/NEW.md" and no uncommitted change

    Examples:
      | request                      | body                         | route              | stage     |
      | Users log in.                | Users log in.                | specifier,coder,qa | specifier |
      | Users log in.\n\nWith email. | Users log in.\n\nWith email. | coder,qa           | coder     |

  # sf task file 2
  Scenario Outline: sf task file 2: the author of a record is git's identity
    Given the git identity of the project is "<identity>"
    When I run sf new with the title "Add login", the request "x" and the route "coder"
    Then the exit status is 0
    And the tip commit of task NEW has the author "<author>"

    Examples:
      | identity                    | author                      |
      | tester <tester@example.com> | tester <tester@example.com> |

  # sf task file 3
  Scenario: sf task file 3: the author of a record is the SwarmForge user when git has no identity
    Given the git identity of the project is "-"
    When I run sf new with the title "Add login", the request "x" and the route "coder"
    Then the exit status is 0
    And the tip commit of task NEW has the author "operator <operator@swarmforge.local>"
