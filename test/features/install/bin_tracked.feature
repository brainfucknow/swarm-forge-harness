Feature: install.sh keeps swarmforge/bin tracked

  A project's .gitignore may ignore every bin/ directory, as the standard
  Visual Studio .gitignore does with "[Bb]in/". install.sh writes
  swarmforge/.gitignore so that git still sees every file it installs under
  swarmforge/bin. The project's own .gitignore gains no negation rule, and
  other bin/ directories stay ignored. A rule that ignores swarmforge/
  itself is out of reach of a file inside it and is not covered.

  Background:
    Given a git repository with one commit and no swarmforge

  # install.sh keeps swarmforge/bin tracked 1
  Scenario Outline: install.sh keeps swarmforge/bin tracked 1: files under swarmforge/bin are staged under a bin/ ignore rule
    Given the file ".gitignore" in the repository holds "<rule>"
    And the file "src/App/bin/App.dll" in the repository holds "x"
    When I run install.sh into it 1 times
    Then the exit status is 0
    And git add -A in the repository stages every file under "swarmforge/bin"
    And git add -A in the repository stages no file under "src/App/bin"
    And the file ".gitignore" in the repository has no line that starts with "!"

    Examples:
      | rule        |
      | [Bb]in/     |
      | bin/        |
      | bin         |
      | **/[Bb]in/* |
      | **/bin/**   |

  # install.sh keeps swarmforge/bin tracked 2
  Scenario: install.sh keeps swarmforge/bin tracked 2: a second run adds no line to swarmforge/.gitignore twice
    Given the file ".gitignore" in the repository holds "[Bb]in/"
    When I run install.sh into it 2 times
    Then the exit status is 0
    And the file "swarmforge/.gitignore" in the repository has no line twice
    And git add -A in the repository stages every file under "swarmforge/bin"

  # install.sh keeps swarmforge/bin tracked 3
  Scenario: install.sh keeps swarmforge/bin tracked 3: lines already in swarmforge/.gitignore are kept
    Given the file ".gitignore" in the repository holds "[Bb]in/"
    And the file "swarmforge/.gitignore" in the repository holds "scratch/"
    When I run install.sh into it 1 times
    Then the exit status is 0
    And the file "swarmforge/.gitignore" in the repository has the line "scratch/"
    And git add -A in the repository stages every file under "swarmforge/bin"
