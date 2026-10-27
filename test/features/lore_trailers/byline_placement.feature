Feature: sf commit byline placement

  The commit-msg hook that sf init and sf start install still adds the
  byline "By <role>." to a commit in a task worktree when the message has no
  such line. When the message ends in a trailer block (the last paragraph,
  as git interpret-trailers --parse reads it), the hook puts the byline in a
  paragraph of its own just before that block, so git still reads the
  trailers. When the message has no trailer block, the hook adds the byline
  at the end, as before. A message that has no Lore trailer gets no Lore-id.

  Background:
    Given a project with swarmforge initialized
    And a task at stage "qa" started with sf start

  # sf commit byline placement 1
  Scenario Outline: sf commit byline placement 1: the byline goes before a trailer block and after other text
    When I commit "<message>" in the task worktree
    Then the exit status is 0
    And the commit message is "<result>"

    Examples:
      | message                                                    | result                                                                |
      | Fix                                                        | Fix\n\nBy qa.                                                         |
      | Fix\n\nBody text                                           | Fix\n\nBody text\n\nBy qa.                                            |
      | Fix\n\nCo-Authored-By: A <a@b>                             | Fix\n\nBy qa.\n\nCo-Authored-By: A <a@b>                              |
      | Fix\n\nBody text\n\nSigned-off-by: A <a@b>\nSession: s1    | Fix\n\nBody text\n\nBy qa.\n\nSigned-off-by: A <a@b>\nSession: s1     |
      | Fix\n\nBy qa.\n\nCo-Authored-By: A <a@b>                   | Fix\n\nBy qa.\n\nCo-Authored-By: A <a@b>                              |
      | Fix\n\nBody text\nBy qa.                                   | Fix\n\nBody text\nBy qa.                                              |

  # sf commit byline placement 2
  Scenario: sf commit byline placement 2: git reads the trailers of a signed commit
    When I commit "Fix\n\nCo-Authored-By: A <a@b>\nSession: s1" in the task worktree
    Then git reads the trailers of the commit as "Co-Authored-By: A <a@b>\nSession: s1"
