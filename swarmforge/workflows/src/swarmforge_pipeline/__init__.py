"""SwarmForge pipeline on Mistral Workflows.

One workflow execution per task, one Agents SDK agent per role, sf
bookkeeping on the task branch. Gate and spec-judge roles run as workflow
steps that record nothing.
"""
