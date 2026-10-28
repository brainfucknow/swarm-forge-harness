Feature: sf task ids

  sf new makes a task id from the title: a slug, "-", and 7 random lowercase
  hexadecimal digits, and prints it. The slug is the title with its letters
  decomposed and their combining marks dropped, lowercased, and each run of
  characters other than a-z and 0-9 made one "-", with "-" removed at both
  ends. An empty slug is "task". A slug longer than 30 characters is cut to
  its longest prefix of at most 30 characters that ends just before a "-";
  when the first word alone is longer than 30 characters, it is cut at 30.
  The task branch is swarm/<id>.

  A command takes a task as the full id, as the 7-digit hex part of exactly
  one task, or as a prefix of exactly one full id. An argument that matches
  more than one task is refused with "task id '<argument>' is ambiguous:"
  and the matching ids, sorted and separated by spaces; one that matches none
  is refused with "no task '<argument>'". The exit status is 1.

  Background:
    Given a project with swarmforge initialized

  # sf task ids 1
  Scenario Outline: sf task ids 1: sf new makes the id from the slug of the title and seven hex digits
    When I run sf new with the title "<title>"
    Then the exit status is 0
    And the output matches "^<slug>-[0-9a-f]{7}$"
    And the branch "swarm/NEW" exists
    And the file "tasks/NEW.md" on the branch of task NEW has the line "# <heading>"

    Examples:
      | title                                   | heading                                 | slug                           |
      | Add login                               | Add login                               | add-login                      |
      | Fix: the API's 2 bugs!                  | Fix: the API's 2 bugs!                  | fix-the-api-s-2-bugs           |
      | Ändra inloggningen för användare        | Ändra inloggningen för användare        | andra-inloggningen-for         |
      | twelve chars one two three fou          | twelve chars one two three fou          | twelve-chars-one-two-three-fou |
      | twelve chars one two three four         | twelve chars one two three four         | twelve-chars-one-two-three     |
      | Supercalifragilisticexpialidocious word | Supercalifragilisticexpialidocious word | supercalifragilisticexpialidoc |
      | !!!                                     | !!!                                     | task                           |

  # sf task ids 2
  Scenario: sf task ids 2: two tasks with the same title get different ids
    Given task "A" titled "Add login" was created on route "specifier"
    When I run sf new with the title "Add login"
    Then the exit status is 0
    And the output matches "^add-login-[0-9a-f]{7}$"
    And the output is not "$A"

  # sf task ids 3
  Scenario Outline: sf task ids 3: a command takes the full id, the hex part or a unique prefix
    Given task "A" titled "Add login" was created on route "specifier"
    And task "B" titled "Add logout" was created on route "coder"
    When I run "sf stage <argument>"
    Then the exit status is 0
    And the output is "<stage>"

    Examples:
      | argument | stage     |
      | $A       | specifier |
      | $A:hex   | specifier |
      | $B:hex   | coder     |
      | add-logi | specifier |
      | add-logo | coder     |

  # sf task ids 4
  Scenario Outline: sf task ids 4: an argument that matches more than one task or none is refused
    Given task "A" titled "Add login" was created on route "specifier"
    And task "B" titled "Add logout" was created on route "coder"
    When I run "sf stage <argument>"
    Then the exit status is 1
    And the error output has the line "<error>"

    Examples:
      | argument | error                                       |
      | add-log  | sf: task id 'add-log' is ambiguous: $A $B   |
      | nothing  | sf: no task 'nothing'                       |
