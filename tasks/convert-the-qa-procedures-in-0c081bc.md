---
id: convert-the-qa-procedures-in-0c081bc
created: 2026-10-28
---

# Convert the QA procedures in test/qa into tests, then delete them

The operator asked: test/qa holds many Markdown QA procedures (one per task, written by the specifier); convert them to tests, then delete them. Go through every check in test/qa/*.md, including the ones open PRs add or change (#29 clj_mutate_note.md, #30 finish_resume.md, #31 sf_tool.md): drop checks the Gherkin features and specs already cover, turn the automatable rest into acceptance features or specs, and list what cannot run in CI (real tools, fresh clones) for the operator. Then delete test/qa/. Also settle with the operator where future QA procedures go so the folder does not grow back (the specifier and qa role prompts say the procedure is a committed file).
