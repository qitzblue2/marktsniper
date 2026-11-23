---
name: token-efficiency
description: Cut wasted tokens on tool calls and context, not just replies. Use when the user says /token-efficiency, "save tokens", "be efficient", or when a task involves large files or many tool calls. Pairs with /caveman for terse output.
---

# Token efficiency

## No sycophancy
- No praise, agreement padding, or apologies. Disagree plainly when the user is wrong.
- Don't restate the request before answering.

## Tool-call budget
- Plan before calling: decide which files you need, then fetch them in one parallel batch.
- Search (Grep/Glob) before reading. Read only the line ranges you need.
- Never re-read a file already in context unless it changed.
- Don't re-run a command to "double check" when its output is already in context.
- Stop exploring once you have enough to act.
- Prefer one targeted test over the full suite while iterating.

## Read before write
- Read a file's relevant section before editing it. Never edit blind.
- Use small Edit calls, not full-file rewrites, for small changes.

## Output
- Answer first. Skip summaries of what you just did when the diff shows it.
- Don't paste large file contents or long logs back to the user; cite `path:line`.
- No unrequested extras: no bonus refactors, docs, or tests unless asked.

## Context hygiene
- Suggest `/clear` when switching to an unrelated task.
- Suggest `/compact` when the conversation gets long.
