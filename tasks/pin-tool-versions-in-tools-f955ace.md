---
id: pin-tool-versions-in-tools-f955ace
created: 2026-10-31
---

# Pin tool versions in tools.conf

Split from 'Tighten worker permissions, start directory and tool versions'. The engineering rules require the latest upstream tools, but sf tool ensure reuses an existing clone unchanged and cloverage and speclj install at Maven RELEASE. The operator chose: a ref column in tools.conf (full commit SHA for GitHub rows, Maven version for clj rows, '-' for local); sf tool ensure fetches and checks out that ref in an existing or new clone and prints it (INSTALLED: <tool> <path> @<sha>), which roles cite in Tested trailers; an upgrade is a reviewed commit that edits the column; engineering.md says 'the version pinned in tools.conf' instead of 'latest'. sf-cs, sf-js and sf-fs package versions stay out of scope.
