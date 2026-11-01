Feature: sf tool pinned refs

  Each GitHub row of swarmforge/tools.conf pins a full commit SHA in its ref
  column. sf tool ensure puts the tool's clone under .swarmforge/tools at that
  commit, detached, with no local changes: it clones a missing clone, moves an
  existing one, discards edits to tracked files, and removes untracked files
  that are not ignored. It fetches the commit from https://<source>.git only
  when the clone lacks it, and never through the clone's origin remote, so
  ensure works offline once the clone has the commit. It runs no git hook of
  the clone. It checks that the clone's HEAD is the pinned commit, writes the
  wrapper, and prints "INSTALLED: <tool> <wrapper> @<sha>".

  If the pinned commit cannot be fetched, ensure prints an error naming the
  tool on stderr, exits 1, and leaves an earlier wrapper byte-for-byte as it
  was. A directory under .swarmforge/tools that is not the top level of its
  own git clone fails the install; ensure changes nothing in it and nothing in
  the project's own repository. A SWARMFORGE_TOOL_SRC override is used as it
  is: never fetched, checked out or cleaned, and its INSTALLED line has no @
  suffix.

  Background:
    Given a project with swarmforge initialized
    And a fake bb on PATH
    And no tool source override
    And the upstream "dry4go" has the commits "PIN NEXT"
    And the main checkout's tools.conf pins "dry4go" at "PIN"

  # sf tool pinned refs 1
  Scenario: sf tool pinned refs 1: a new clone is checked out at the pinned commit
    When I run "sf tool ensure dry4go" in the main checkout
    Then the exit status is 0
    And the output has the line "INSTALLED: dry4go MAIN/.swarmforge/bin/dry4go @PIN"
    And the clone is clean at "PIN"

  # sf tool pinned refs 2
  Scenario Outline: sf tool pinned refs 2: an existing clone is put back at the pinned commit
    Given an earlier clone with <change>
    When I run "sf tool ensure dry4go" in the main checkout
    Then the exit status is 0
    And the output has the line "INSTALLED: dry4go MAIN/.swarmforge/bin/dry4go @PIN"
    And the clone is clean at "PIN"

    Examples:
      | change                                     |
      | HEAD at NEXT                               |
      | a local commit on top of PIN               |
      | an edited tracked file                     |
      | an untracked file                          |
      | only NEXT and an unreachable origin remote |

  # sf tool pinned refs 3
  Scenario: sf tool pinned refs 3: a clone that has the pinned commit needs no network
    Given an earlier clone with HEAD at NEXT
    And the upstream "dry4go" is unreachable
    When I run "sf tool ensure dry4go" in the main checkout
    Then the exit status is 0
    And the output has the line "INSTALLED: dry4go MAIN/.swarmforge/bin/dry4go @PIN"
    And the clone is clean at "PIN"

  # sf tool pinned refs 4
  Scenario Outline: sf tool pinned refs 4: a commit that cannot be fetched fails the install and keeps the earlier wrapper
    Given the main checkout's tools.conf pins "dry4go" at "<ref>"
    And the upstream "dry4go" is <upstream>
    And an earlier install left a wrapper for "dry4go"
    When I run "sf tool ensure dry4go" in the main checkout
    Then the exit status is 1
    And the error output contains "sf tool: dry4go: cannot fetch <ref> from https://github.com/unclebob/dry4go.git"
    And the wrapper for "dry4go" is unchanged

    Examples:
      | ref                                      | upstream    |
      | PIN                                      | unreachable |
      | aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa | reachable   |

  # sf tool pinned refs 5
  Scenario: sf tool pinned refs 5: a directory that is not its own clone fails untouched
    Given the directory "CLONE" exists without bb.edn
    And an earlier install left a wrapper for "dry4go"
    And a snapshot of "CLONE"
    And a snapshot of the main checkout's git state
    When I run "sf tool ensure dry4go" in the main checkout
    Then the exit status is 1
    And the error output contains "sf tool: dry4go: CLONE is not the top level of a git clone"
    And "CLONE" matches the snapshot
    And the main checkout's git state matches the snapshot
    And the wrapper for "dry4go" is unchanged

  # sf tool pinned refs 6
  Scenario: sf tool pinned refs 6: ensure runs no hook of the clone
    Given an earlier clone with HEAD at NEXT
    And the clone has a post-checkout hook that writes "MAIN/hooked"
    When I run "sf tool ensure dry4go" in the main checkout
    Then the exit status is 0
    And the clone is clean at "PIN"
    And there is no file "MAIN/hooked"

  # sf tool pinned refs 7
  Scenario: sf tool pinned refs 7: an override is used as it is and is not reported as pinned
    Given a tool source whose bb.edn defines the tasks "dry4go"
    And a snapshot of "SRC"
    When I run "sf tool ensure dry4go" in the main checkout
    Then the exit status is 0
    And the output has the line "INSTALLED: dry4go MAIN/.swarmforge/bin/dry4go"
    And "SRC" matches the snapshot
    And there is no file "CLONE"
