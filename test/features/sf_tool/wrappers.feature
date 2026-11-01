Feature: sf tool wrappers

  sf tool ensure writes one wrapper per tool in .swarmforge/bin under the main
  checkout. A wrapper for a tool of kind bb runs
  "bb --config <source>/bb.edn <task>". A wrapper for a tool of the new kind
  bbmain runs "bb -cp <source>/src -m <namespace>", where the catalogue's task
  column names the main namespace. The catalogue names the entry points that
  upstream defines: clj-mutate runs the task mutate, crap4clj runs the task
  crap, and dry4clj (kind bbmain) runs the namespace dry4clj.core. The
  wrapper's own arguments follow, after any argument rewriting the tool has.
  An ensure that succeeds replaces a wrapper left by an earlier install.
  The wrapper passes the task column to bb (or the main namespace to
  clojure) as one quoted word, so no character in it is read by the shell.
  sf check accepts the kinds bb, bbmain, clj and local.

  Background:
    Given a project with swarmforge initialized
    And a fake bb on PATH
    And a tool source whose bb.edn defines the tasks "mutate crap gherkin-parser"
    And the tool source has the namespace "dry4clj.core"

  # sf tool wrappers 1
  Scenario Outline: sf tool wrappers 1: each wrapper runs the upstream entry point
    Given "sf tool ensure <tool>" ran in the main checkout
    When I run the <tool> wrapper with "<args>"
    Then bb received "<received>"

    Examples:
      | tool           | args                    | received                                                |
      | clj-mutate     | src/a.clj --mutate-all  | --config SRC/bb.edn mutate src/a.clj --max-workers 4    |
      | crap4clj       | --use-existing-coverage | --config SRC/bb.edn crap --use-existing-coverage        |
      | dry4clj        | src spec                | -cp SRC/src -m dry4clj.core src spec                    |
      | gherkin-parser | a.feature tmp/a.json    | --config SRC/bb.edn gherkin-parser a.feature tmp/a.json |

  # sf tool wrappers 2
  Scenario: sf tool wrappers 2: ensure reports the installed wrapper
    When I run "sf tool ensure dry4clj" in the main checkout
    Then the exit status is 0
    And the output has the line "INSTALLED: dry4clj MAIN/.swarmforge/bin/dry4clj"

  # sf tool wrappers 3
  Scenario: sf tool wrappers 3: sf check accepts the bbmain kind
    When I run "sf check" in the main checkout
    Then the exit status is 0
    And the output does not mention "tools.conf"

  # sf tool wrappers 4
  Scenario: sf tool wrappers 4: a successful ensure replaces an earlier wrapper
    Given an earlier install left a wrapper for "gherkin-parser"
    And "sf tool ensure gherkin-parser" ran in the main checkout
    When I run the gherkin-parser wrapper with "a.feature"
    Then bb received "--config SRC/bb.edn gherkin-parser a.feature"

  # sf tool wrappers 5
  Scenario Outline: sf tool wrappers 5: the task column reaches bb as one word
    Given the tool source has the namespace "<namespace>"
    And a task worktree
    And the task worktree's tools.conf adds the line "odd bbmain github.com/x/odd 0123456789abcdef0123456789abcdef01234567 <namespace> - -"
    And "sf tool ensure odd" ran in the task worktree
    When I run the odd wrapper with "a"
    Then bb received "-cp SRC/src -m <namespace> a"

    Examples:
      | namespace              |
      | x;touch${IFS}injected  |
