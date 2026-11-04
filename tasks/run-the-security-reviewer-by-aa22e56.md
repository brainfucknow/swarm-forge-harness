---
id: run-the-security-reviewer-by-aa22e56
created: 2026-11-04
---

# Run the security reviewer by default on features

Operator request (option c): encourage the security review. (1) The feature route includes security-reviewer by default, after hardener and before qa: specifier,coder,cleaner,architect,hardener,security-reviewer,qa. (2) The specifier, when a task touches nothing security-relevant (no authentication, authorization, secrets, untrusted input, external execution or dependency changes), recommends removing security-reviewer from the route in its questions to the operator; the operator decides per task. security-reviewer is catalogued optional; check how the optional flag and named routes interact (swarmforge/roles.conf, routes.conf, sf route/check) and whether the catalogue flag must change. Update the docs that state the feature route (README.md route table, swarmforge/README.md) and any sf test that pins it. Keep text short; no tests of prompt text.
