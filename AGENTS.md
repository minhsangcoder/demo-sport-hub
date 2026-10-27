# SportHub — instructions for every agent

Before planning, applying, fixing, refactoring or reviewing, read
`openspec/CURRENT_CHANGE.md`. It selects the only authoritative proposal.
This applies to OpenSpec, every Ponytail skill, Codex and other IDE agents.

- Follow the user's latest explicit instructions first, then the selected proposal
  and its own design/specs/tasks. Do not merge requirements from older changes.
- Do not select a change by modification time, unfinished tasks or old chat context.
- A completed current change stays current; never fall back to an older change.
- When generating a new proposal, maintain the pointer and history-only notices
  as required by `openspec/CURRENT_CHANGE.md` before handing it off.
- Read implementation and tests to understand the system, not to override the
  latest requirements. Preserve unrelated working features and existing data.
- Announce the selected change before work. If its artifacts conflict, surface
  the conflict instead of filling the gap from an old proposal.

Nested technical instructions (such as `frontend/AGENTS.md`) still apply;
they do not change which business proposal is authoritative.
