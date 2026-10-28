Feature: Language guidance outside shared engineering rules

  This is a manual documentation acceptance contract, not an executable
  prompt-text test. The QA procedure is test/qa/language_guidance.md.

  Background:
    Given the installed SwarmForge constitution and language guides

  # Language guidance 1
  Scenario Outline: Language guidance 1: each supported language has one discoverable reference
    When an agent follows the engineering rules for a "<language>" project
    Then it is directed to read "<guide>"
    And that guide contains the existing tool mappings, defaults and verification exceptions for that language

    Examples:
      | language              | guide                              |
      | Go                    | swarmforge/languages/go.md          |
      | Clojure or Babashka    | swarmforge/languages/clojure.md     |
      | Java                  | swarmforge/languages/java.md        |
      | C#                    | swarmforge/languages/csharp.md      |
      | JavaScript/TypeScript | swarmforge/languages/javascript.md  |

  # Language guidance 2
  Scenario: Language guidance 2: the shared rules remain complete
    When an agent reads engineering.md
    Then startup, design, acceptance, verification and guardrail policies remain shared
    And language-specific tool mappings, defaults and mutation exceptions live in the language guides
    And shared acceptance-tool runtime preferences still apply to every project

  # Language guidance 3
  Scenario: Language guidance 3: relocation preserves the existing requirements
    When QA compares the documentation with the task's base revision
    Then every existing language-specific requirement is retained without changing its meaning
    And an installed project can follow the references without this repository's root README
    And no automated test pins constitution or prompt wording
