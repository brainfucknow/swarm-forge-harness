Feature: sf tool write guard

  Before it writes anything, sf tool ensure checks the kind and the rewrite
  of the tool and of every tool in its needs chain (the tools it needs, the
  tools those need, and so on). When one of them has a kind or a rewrite that
  sf tool does not know, ensure prints the error on stderr and exits 1. It
  writes no wrapper, clones no source, and writes no coverage config: nothing
  under the main checkout's .swarmforge/ changes, so wrappers left by an
  earlier install stay byte-for-byte as they were and require still reports
  them. This is what happens when a task worktree's tools.conf has a kind or a
  rewrite that its sf does not know.

  # sf tool write guard 1
  Scenario Outline: sf tool write guard 1: an unknown kind or rewrite in the needs chain writes nothing
    Given a project with swarmforge initialized
    And no tool source override
    And a task worktree
    And the task worktree's tools.conf adds the line "weird java github.com/x/weird 0123456789abcdef0123456789abcdef01234567 crap - cloverage"
    And the task worktree's tools.conf adds the line "odd bb github.com/x/odd 0123456789abcdef0123456789abcdef01234567 crap rot13 cloverage"
    And the task worktree's tools.conf adds the line "outer bb github.com/x/outer 0123456789abcdef0123456789abcdef01234567 crap coverage weird"
    And an earlier install left a wrapper for "cloverage"
    And an earlier install left a wrapper for "weird"
    And an earlier install left a wrapper for "<tool>"
    And a snapshot of "MAIN/.swarmforge"
    When I run "sf tool ensure <tool>" in the task worktree
    Then the exit status is 1
    And the error output contains "<error>"
    And "MAIN/.swarmforge" matches the snapshot
    And "sf tool require <tool>" in the task worktree prints "OK: <tool> MAIN/.swarmforge/bin/<tool>"

    Examples:
      | tool  | error                                                     |
      | weird | sf tool: unknown kind 'java' for weird                    |
      | odd   | sf tool: unknown rewrite 'rot13' in swarmforge/tools.conf |
      | outer | sf tool: unknown kind 'java' for weird                    |
