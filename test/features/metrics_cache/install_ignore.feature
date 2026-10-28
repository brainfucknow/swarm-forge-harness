Feature: sf install ignores manifests

  install.sh adds the line ".metrics/" to the .gitignore of the target
  project, as it does for .worktrees/, .swarmforge/ and tmp/. The line is
  there once, also when install.sh runs again.

  # sf install ignores manifests 1
  Scenario Outline: sf install ignores manifests 1: install.sh adds .metrics/ to .gitignore once
    Given a git repository with one commit and no swarmforge
    When I run install.sh into it <times> times
    Then the exit status is 0
    And the .gitignore of the repository has the line ".metrics/" exactly once

    Examples:
      | times |
      | 1     |
      | 2     |
