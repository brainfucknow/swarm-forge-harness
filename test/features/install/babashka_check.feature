Feature: install.sh checks Babashka before it changes the project

  install.sh needs Babashka 1.12.205 or newer to run sf. It checks bb
  before it copies, creates or edits anything in the target project. When
  no bb is on PATH, install.sh exits non-zero, its error output names
  Babashka and the minimum version, and the target project is exactly as
  it was. A bb older than the minimum is refused the same way, with the
  refusal line sf itself prints; that case runs against real Babashka
  binaries and is verified by manual QA, because a test cannot make bb
  report another version.

  Background:
    Given a git repository with one commit and no swarmforge
    And the file "AGENTS.md" in the repository holds "Project notes."
    And the file ".gitignore" in the repository holds "build/"

  # install.sh checks Babashka before it changes the project 1
  Scenario: install.sh checks Babashka before it changes the project 1: no bb on PATH leaves the project untouched
    Given no bb is on PATH
    When I run install.sh into it 1 times
    Then the exit status is not 0
    And the error output has a line containing "Babashka" and "1.12.205 or newer"
    And the repository has the same files with the same contents as before install.sh ran
    And the repository has no file "swarmforge/bin/sf"
    And the repository has no file ".git/hooks/commit-msg"
