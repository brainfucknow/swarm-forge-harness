---
id: keep-finished-task-files-out-a9c0a92
created: 2026-10-31
---

# Keep finished task files out of the repository

The operator wants a clean repository: finished task files should not accumulate in tasks/done/. Today sf finish moves tasks/<id>.md to tasks/done/<id>.md, which is also how sf tells a finished task (including a squash-merged one) and what sf show reads after the branch is gone. Change sf so a finished task leaves no file in the tree, keeping its history readable from git (the record commits and the finished merge), and remove the existing tasks/done/ archives. Settle with the operator how sf still recognises a finished task, especially after a squash merge, and what sf show prints for one.
