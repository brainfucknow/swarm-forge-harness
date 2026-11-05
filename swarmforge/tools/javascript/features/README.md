# sf-js acceptance features

Executable contracts for the [JavaScript wrapper](../../../languages/javascript.md).
Run `bb --config swarmforge/tools/javascript/bb.edn acceptance` from the
repository root. Each execution uses the real `sf-js` in a scratch Git
repository with fake Node and package-manager tools. Fakes record arguments,
return chosen statuses, and write supplied linter, coverage, clone, and
mutation reports. Real-tool compatibility still needs the checks in the
language guide.

Fixture operations and assertions are defined by the
[step handlers](../acceptance/src/sfjs/acceptance/steps.clj),
[world](../acceptance/src/sfjs/acceptance/world.clj), and
[fake tools](../acceptance/fake-tool.sh). Tool calls are observed through
the wrapper's `$ ` output lines; numbered output assertions exclude those
lines. Default fakes succeed and return empty findings; oxlint reports one
enabled rule. Scenario steps override these defaults.

## Example tables

`gherkin-mutator` changes one example cell at a time:

- Give expected output its own column even when it repeats an input, so a
  mutated input can fail the comparison.
- Keep values fixed when small mutations cannot change the outcome, such
  as any positive failure status or a line wholly inside a range. Use a
  plain scenario sharing the outline's index when appropriate.
