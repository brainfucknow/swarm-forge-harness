Feature: sf commit Lore-id

  A Lore trailer is a trailer in the trailer block whose key is one of
  Constraint, Rejected, Confidence, Scope-risk, Reversibility, Directive,
  Tested, Not-tested, Supersedes, Depends-on, Related or Lore-id. When the
  trailer block of a task worktree commit has a Lore trailer and no Lore-id
  trailer, the commit-msg hook adds "Lore-id: <id>" as the first line of the
  block. <id> is 8 random lowercase hexadecimal digits. An existing Lore-id
  stays as it is, and no second one is added. The hook adds a Lore-id only
  where it would add a byline: in a task worktree whose role it knows, at a
  stage other than done.

  Background:
    Given a project with swarmforge initialized
    And a task at stage "qa" started with sf start

  # sf commit Lore-id 1
  Scenario Outline: sf commit Lore-id 1: a Lore trailer gets a Lore-id as the first trailer
    When I commit "Fix\n\n<key>: x" in the task worktree
    Then the exit status is 0
    And the commit message is "Fix\n\nBy qa.\n\nLore-id: LID\n<key>: x"
    And LID is 8 lowercase hexadecimal digits

    Examples:
      | key           |
      | Constraint    |
      | Rejected      |
      | Confidence    |
      | Scope-risk    |
      | Reversibility |
      | Directive     |
      | Tested        |
      | Not-tested    |
      | Supersedes    |
      | Depends-on    |
      | Related       |

  # sf commit Lore-id 2
  Scenario Outline: sf commit Lore-id 2: a message without a Lore trailer in the last paragraph gets no Lore-id
    When I commit "<message>" in the task worktree
    Then the exit status is 0
    And the commit message is "<result>"

    Examples:
      | message                                       | result                                                |
      | Fix\n\nTested: x\n\nClosing text              | Fix\n\nTested: x\n\nClosing text\n\nBy qa.            |
      | Fix\n\nTested-by: A <a@b>                     | Fix\n\nBy qa.\n\nTested-by: A <a@b>                   |

  # sf commit Lore-id 3
  Scenario Outline: sf commit Lore-id 3: an existing Lore-id stays as it is
    When I commit "<message>" in the task worktree
    Then the exit status is 0
    And the commit message is "<result>"

    Examples:
      | message                                   | result                                            |
      | Fix\n\nLore-id: 0badc0de\nTested: x       | Fix\n\nBy qa.\n\nLore-id: 0badc0de\nTested: x     |
      | Fix\n\nTested: x\nLore-id: 0badc0de       | Fix\n\nBy qa.\n\nTested: x\nLore-id: 0badc0de     |
      | Fix\n\nLore-id: 0badc0de                  | Fix\n\nBy qa.\n\nLore-id: 0badc0de                |

  # sf commit Lore-id 4
  Scenario Outline: sf commit Lore-id 4: the hook leaves a message alone where it adds no byline
    When I commit "Fix\n\nTested: x" in the <place>
    Then the exit status is 0
    And the commit message is "Fix\n\nTested: x"

    Examples:
      | place                                      |
      | main checkout                              |
      | task worktree with SWARMFORGE_ROLE "done"  |

  # sf commit Lore-id 5
  Scenario: sf commit Lore-id 5: each commit gets its own Lore-id
    When I commit "Fix\n\nTested: x" in the task worktree twice
    Then the two commits have different Lore-ids
