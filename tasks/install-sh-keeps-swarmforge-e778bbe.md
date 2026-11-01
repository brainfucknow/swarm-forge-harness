---
id: install-sh-keeps-swarmforge-e778bbe
created: 2026-11-01
---

# install.sh keeps swarmforge/bin tracked under .NET bin/ ignore rules

Operator: "install.sh: keep swarmforge/bin/ from being swallowed by .NET's ignore rule for every bin/ directory. This silently dropped the sf scripts in this repo. There is an easy way to avoid that, by adding a .gitignore to the specific folder telling git to not ignore the things."

Observed in auctions-api-fsharp-mainstream: its standard Visual Studio .gitignore has [Bb]in/ (line 30), so after install.sh and 'commit the result', swarmforge/bin/ (sf, sf-cs, sf-fs, sf-js, sf-tool, sf-vb) was never committed; every task worktree lacked swarmforge/bin and roles had to call the main checkout's copy. Nothing warned.

Orchestrator check of the operator's approach in a scratch repo (git 2.x): a .gitignore placed inside swarmforge/bin/ is never read, because the parent rule [Bb]in/ excludes the directory itself and git does not descend into it. A .gitignore in swarmforge/ containing '!bin/' does re-include swarmforge/bin/ under both '[Bb]in/' and 'bin/' root rules. So the folder-local file belongs in swarmforge/, not swarmforge/bin/.
