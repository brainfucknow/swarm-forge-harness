Feature: sf adapters preserve project agents

  Adapter generation owns its generated files, not the shared harness
  directories. Installation uses the same preservation and collision rules.
  Generated prompt wording is not asserted by these scenarios.

  Background:
    Given a scratch git project prepared for adapter generation

  # sf adapters preserve project agents 1
  Scenario Outline: sf adapters preserve project agents 1: unrelated agents survive generation and installation
    Given an unrelated project agent at "<path>" containing "<contents>"
    When I run "<command>" in the scratch project
    Then the exit status is 0
    And "<path>" still contains exactly "<contents>"
    And adapters exist for the configured subagent roles in all three harnesses

    Examples:
      | command     | path                                      | contents          |
      | sf adapters | .claude/agents/project-specialist.md       | project specialist |
      | sf adapters | .github/agents/project-specialist.agent.md | repository reviewer |
      | sf adapters | .codex/agents/project-specialist.toml      | custom specialist |
      | install.sh  | .claude/agents/project-specialist.md       | project specialist |
      | install.sh  | .github/agents/project-specialist.agent.md | repository reviewer |
      | install.sh  | .codex/agents/project-specialist.toml      | custom specialist |

  # sf adapters preserve project agents 2
  Scenario: sf adapters preserve project agents 2: managed adapters follow configuration changes
    Given adapters were generated for the configured roles
    And an unrelated project agent at ".codex/agents/project-specialist.toml" containing "keep me"
    And the role "qa" is changed from subagent to session
    And the role "coder" uses the tier "fast"
    When I run "sf adapters" in the scratch project
    Then the exit status is 0
    And no generated adapter remains for "qa" in any harness
    And the generated adapters for "coder" use the configured tier settings
    And ".codex/agents/project-specialist.toml" still contains exactly "keep me"
    And a second "sf adapters" run succeeds without changing agent files

  # sf adapters preserve project agents 3
  Scenario Outline: sf adapters preserve project agents 3: an unowned target collision is reported without changing agent files
    Given the scratch project has previously generated coder adapters only
    And an unrelated project agent occupies the new role target "<path>" containing "<contents>"
    When I run "<command>" in the scratch project
    Then the exit status is 1
    And the error output identifies "<path>" as a conflicting project agent
    And no file in the three harness agent directories was created, changed or removed

    Examples:
      | command     | path                                | contents        |
      | sf adapters | .claude/agents/specialist.md         | project owner   |
      | sf adapters | .github/agents/specialist.agent.md   | private reviewer |
      | sf adapters | .codex/agents/specialist.toml        | local specialist |
      | install.sh  | .claude/agents/specifier.md          | project owner   |
      | install.sh  | .github/agents/specifier.agent.md    | private reviewer |
      | install.sh  | .codex/agents/specifier.toml         | local specialist |

  # sf adapters preserve project agents 4
  Scenario: sf adapters preserve project agents 4: existing generated adapters remain updatable without local ownership state
    Given the project holds the previously shipped generated adapters
    And no local adapter ownership state is present
    When I run "sf adapters" in the scratch project
    Then the exit status is 0
    And adapters exist for the configured subagent roles in all three harnesses
    And a second "sf adapters" run succeeds without changing agent files
