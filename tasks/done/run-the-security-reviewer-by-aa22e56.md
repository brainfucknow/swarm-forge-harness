---
id: run-the-security-reviewer-by-aa22e56
created: 2026-11-04
stage: done
route: specifier,coder,cleaner,architect,hardener,qa
---

# Run the security reviewer by default on features

Operator request (option c): encourage the security review. (1) The feature route includes security-reviewer by default, after hardener and before qa: specifier,coder,cleaner,architect,hardener,security-reviewer,qa. (2) The specifier, when a task touches nothing security-relevant (no authentication, authorization, secrets, untrusted input, external execution or dependency changes), recommends removing security-reviewer from the route in its questions to the operator; the operator decides per task. security-reviewer is catalogued optional; check how the optional flag and named routes interact (swarmforge/roles.conf, routes.conf, sf route/check) and whether the catalogue flag must change. Update the docs that state the feature route (README.md route table, swarmforge/README.md) and any sf test that pins it. Keep text short; no tests of prompt text.

## Records

```text
type: created
from: operator
branch: swarm/run-the-security-reviewer-by-aa22e56
commit: d2a5c82
route: specifier,coder,cleaner,architect,hardener,qa
```

```text
type: question
from: specifier
question: 1. Keep the optional flag on security-reviewer? sf ignores the flag for named routes, so adding it to feature in routes.conf works as is. The flag only matters when routes.conf has no feature route; then sf builds the route from the non-optional roles. (a) Keep it: no sf code change, and that fallback still skips security review. (b) Drop it: that fallback gets security review too. Recommended: (a).
  2. What counts as security-relevant? (a) Only the six areas in the request: authentication, authorization, secrets, untrusted input, external execution, dependency changes. Anything else means the specifier recommends removal, and when unsure it keeps the reviewer. (b) A wider list, for example file system or network access, cryptography, or logging sensitive data. Recommended: (a). It matches the security reviewer's own scope.
  3. Where does the specifier recommend removal? (a) Always in its sf ask round, which forces a question round on every non-security feature. (b) In the sf ask round if it is asking anyway, otherwise at sf review; the operator then runs sf route before approving. (c) Only at sf review. Recommended: (b).
```

```text
type: answer
from: operator
answer: All recommended: 1a keep the optional flag (sf ignores it for named routes; the fallback route still skips security review); 2a security-relevant means authentication, authorization, secrets, untrusted input, external execution, dependency changes, and when unsure the specifier keeps the reviewer; 3b the specifier recommends removal in its sf ask round if it is asking anyway, otherwise at sf review, and the operator runs sf route before approving.
```

```text
type: review
from: specifier
commit: 8b2a54c
files: -
summary: Feature route gains security-reviewer; specifier recommends removal for non-security tasks
```

```text
type: approval
from: operator
```

```text
type: handoff
from: specifier
to: coder
branch: swarm/run-the-security-reviewer-by-aa22e56
commit: 8b2a54c
files: -
summary: specifier work complete
```

```text
type: handoff
from: coder
to: cleaner
branch: swarm/run-the-security-reviewer-by-aa22e56
commit: 226749f
files: .github/workflows/test.yml,README.md,install.sh,swarmforge/README.md,swarmforge/bin/sf-rb,swarmforge/constitution/articles/engineering.md,swarmforge/languages/csharp.md,swarmforge/languages/fsharp.md,swarmforge/languages/ruby.md,swarmforge/languages/vbnet.md,swarmforge/roles/cleaner.md,swarmforge/roles/security-reviewer.md,swarmforge/roles/specifier.md,swarmforge/routes.conf,swarmforge/tools.conf,swarmforge/tools/core/property/property_support.clj,swarmforge/tools/core/property/sfcore/core_property.clj,swarmforge/tools/core/src/sfcore/crap.clj,swarmforge/tools/core/src/sfcore/jscpd.clj,swarmforge/tools/core/src/sfcore/shell.clj,swarmforge/tools/core/test/sfcore/core_test.clj,swarmforge/tools/csharp/resources/fsharplint.json,swarmforge/tools/csharp/resources/smell.globalconfig,swarmforge/tools/csharp/resources/smell.targets,swarmforge/tools/csharp/src/sfcs/cli.clj,swarmforge/tools/csharp/src/sfcs/cobertura.clj,swarmforge/tools/csharp/test/sfcs/core_test.clj,swarmforge/tools/javascript/bb.edn,swarmforge/tools/javascript/property/sfjs/sfjs_property.clj,swarmforge/tools/javascript/src/sfjs/cli.clj,swarmforge/tools/javascript/src/sfjs/crap.clj,swarmforge/tools/javascript/test/sfjs/architecture_test.clj,swarmforge/tools/javascript/test/sfjs/reports_test.clj,swarmforge/tools/ruby/bb.edn,swarmforge/tools/ruby/fixtures/coverage.json,swarmforge/tools/ruby/fixtures/flay-report.json,swarmforge/tools/ruby/fixtures/flog.txt,swarmforge/tools/ruby/fixtures/jscpd-report.json,swarmforge/tools/ruby/fixtures/ledger.rb,swarmforge/tools/ruby/fixtures/rubocop-cyclomatic.json,+31 more
summary: Feature route gains security-reviewer; tests, docs, specifier rule updated
```

```text
type: handoff
from: cleaner
to: architect
branch: swarm/run-the-security-reviewer-by-aa22e56
commit: c4e74bf
files: swarmforge/README.md,swarmforge/roles/specifier.md
summary: docs trimmed; checks pass
```

```text
type: handoff
from: architect
to: hardener
branch: swarm/run-the-security-reviewer-by-aa22e56
commit: dc01630
files: -
summary: Route design consistent; no structural change
```

```text
type: handoff
from: hardener
to: qa
branch: swarm/run-the-security-reviewer-by-aa22e56
commit: ef57769
files: -
summary: route pins mutation-verified; no change
```

```text
type: handoff
from: qa
to: done
branch: swarm/run-the-security-reviewer-by-aa22e56
commit: 31e9256
files: -
summary: QA verified: feature route includes security-reviewer; fallback unchanged
```
