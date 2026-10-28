Feature: CI manifest guard

  test/no_manifests.sh fails when the git index of the repository holds a
  mutation manifest. It checks the repository of the directory it runs in.
  A manifest is in the index when a tracked file whose name ends in .feature
  has a line that starts with "# mutation-stamp:" or with
  "# acceptance-mutation-manifest-begin" (the gherkin-mutator block), or
  when a tracked file has a directory named .metrics in its path. It checks
  the content git has staged, not the working tree, so untracked, ignored
  and unstaged files do not count.

  For each manifest it prints one line on stderr, with the path from the
  root of the repository: first
  "mutation manifest block in a tracked feature file: <path>" for each
  feature file, then "tracked file under .metrics/: <path>" for each file
  under .metrics/, each group in git's path order. It then exits 1. With no
  manifest it prints nothing and exits 0.

  Background:
    Given a git repository whose .gitignore has the line ".metrics/"
    And the file "features/x.feature" holds "Feature: x" and is committed

  # CI manifest guard 1
  Scenario Outline: CI manifest guard 1: a tracked feature file with a manifest block fails
    Given the file "<path>" holds "<content>" and is <state>
    When I run the manifest guard
    Then the exit status is 1
    And the error output is "mutation manifest block in a tracked feature file: <path>"
    And the output is ""

    Examples:
      | path               | state     | content                                                                                                                  |
      | features/x.feature | committed | # mutation-stamp: sha256=ab\n# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end\n\nFeature: x |
      | a/b/y.feature      | staged    | # acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end\n\nFeature: y                              |
      | z.feature          | committed | Feature: z\n# mutation-stamp: sha256=ab                                                                                  |

  # CI manifest guard 2
  Scenario Outline: CI manifest guard 2: a tracked file under .metrics/ fails
    Given the file "<path>" holds "x" and is committed
    When I run the manifest guard
    Then the exit status is 1
    And the error output is "tracked file under .metrics/: <path>"

    Examples:
      | path                          |
      | .metrics/mutate/a.edn         |
      | tools/sf/.metrics/crap.edn    |

  # CI manifest guard 3
  Scenario Outline: CI manifest guard 3: markers that do not start a line, and other names, pass
    Given the file "<path>" holds "<content>" and is committed
    When I run the manifest guard
    Then the exit status is 0
    And there is no output

    Examples:
      | path               | content                                                                           |
      | features/x.feature | Feature: x\n  Given the block # acceptance-mutation-manifest-begin                |
      | features/x.feature | Feature: x\nText # mutation-stamp: sha256=ab                                      |
      | docs/block.md      | # acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end    |
      | metrics/a.edn      | x                                                                                 |
      | docs/a.metrics     | x                                                                                 |

  # CI manifest guard 4
  Scenario Outline: CI manifest guard 4: files outside the index pass
    Given the file "<path>" holds "<content>"
    When I run the manifest guard
    Then the exit status is 0
    And there is no output

    Examples:
      | path                  | content                                                                       |
      | features/x.feature    | # acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end\n\nFeature: x |
      | features/new.feature  | # mutation-stamp: sha256=ab\n\nFeature: new                                   |
      | .metrics/mutate/a.edn | x                                                                             |

  # CI manifest guard 5
  Scenario: CI manifest guard 5: every manifest is listed, with paths from the root, from any directory
    Given the file "b.feature" holds "# mutation-stamp: sha256=ab\n\nFeature: b" and is committed
    And the file ".metrics/m.edn" holds "x" and is committed
    And the file "a/c.feature" holds "# acceptance-mutation-manifest-begin\n# {}\n# acceptance-mutation-manifest-end\n\nFeature: c" and is committed
    When I run the manifest guard in the directory "a"
    Then the exit status is 1
    And the error output is "mutation manifest block in a tracked feature file: a/c.feature\nmutation manifest block in a tracked feature file: b.feature\ntracked file under .metrics/: .metrics/m.edn"

  # CI manifest guard 6
  Scenario: CI manifest guard 6: a repository without manifests passes
    When I run the manifest guard
    Then the exit status is 0
    And there is no output
