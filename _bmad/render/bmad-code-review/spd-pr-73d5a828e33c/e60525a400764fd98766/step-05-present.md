---
deferred_work_file: 'D:/SPD-PR/_bmad-output/deferred-work.md'
---

# Step 5: Present and Act

## RULES

- When `{plan_file}` is set, always write findings to it before offering action choices.
- Never change a plan's `status`. The build leaves it at `built`; only the user marks a ticket done.
- `{triage_file}` is the source for every finding below: its verdict, evidence, route, options, and rejection reason. Render from it, not from memory.

## INSTRUCTIONS

### 1. Clean review shortcut

If zero findings remain after triage (all rejected or none raised): state that. When `{plan_file}` is set, still write section 2's block, with the summary line and the `Rejected` appendix, so the run is recorded.
Then proceed to section 5.

### 2. Write findings to the plan

If `{plan_file}` is set, append a block to its `## Code Review` section, creating that section at the end of the file when it is absent. The block opens with `### {date}` and the summary line from section 3, then the findings below, then the `Rejected` appendix. Write all findings in this order:

1. **`decision-needed`** findings (unchecked):
   `- [ ] [Review][Decision] <Title> — <Detail> Options: <each option, one line>. Recommended: <option> — <reason>.`

2. **`patch`** findings (unchecked):
   `- [ ] [Review][Patch] <Title> [<file>:<line>]`

3. **`defer`** findings (checked off, marked deferred):
   `- [x] [Review][Defer] <Title> [<file>:<line>] — deferred: <pre-existing, or for maybe-false the evidence that would settle it>`

Also append each `defer` finding to `{deferred_work_file}` under a heading `## Deferred from: code review ({date})`. If `plan_file` is set, include its basename in the heading (e.g., `code review of story-digest-delivery-plan (2026-03-18)`). One bullet per finding with description.

### 3. Present summary

Announce what was written:

> **Code review complete.** <D> `decision-needed`, <P> `patch`, <W> `defer`, <R> rejected.

The `Rejected` appendix is one line per rejected finding: `false` with its refutation, `low` with why it was not worth fixing. Without `plan_file`, it ends the chat listing.


If `plan_file` is set, add: `Findings written to {plan_file}.`
Otherwise add: `Findings are listed above. No plan was provided, so nothing was persisted.`

### 4. Resolve decision-needed findings

If `decision_needed` findings exist, present each one with its detail and the options available. The user must decide — the correct fix is ambiguous without their input. Walk through each finding (or batch related ones) and get the user's call. Once resolved, each becomes a `patch`, `defer`, or is rejected.

If the user chooses to defer, ask: Quick one-line reason for deferring this item? (helps future reviews): — then append that reason to both the finding's bullet in `{plan_file}` and the `{deferred_work_file}` entry.

**HALT** — I am waiting for your numbered choice. Reply with only the number. Do not proceed until you select an option.

### 5. Handle `patch` findings

Write one Markdown block a coding agent can fix the findings from without redoing the review. Name the base it was compared to by full SHA, and the reviewed commit the same way, or say that the change is uncommitted on top of the base. Then, for each `patch` finding (including any resolved from `decision-needed`): what is wrong and how you know; the files or symbols it touches; what the fixed code should do, as an outcome, not a recipe; and how to check the fix, including anything that has to be run to confirm the defect. If there are no `patch` findings, say so. Then, under their own headings, list the `decision-needed` findings still unresolved and the `defer` findings, each with its reason, so the agent knows they are not its to fix.

Nothing was run in this review, so never claim a fix works or a check passed.

Show it in this chat.

**✅ Code review actions complete**

- Decision-needed resolved: <D>
- Patches handled: <P>
- Deferred: <W>
- Rejected: <R>

### 6. Next steps

Present the user with follow-up options:

> **What would you like to do next?**
> 1. **Re-run code review** — address findings and review again
> 2. **Done** — end the workflow

**HALT** — I am waiting for your choice. Do not proceed until the user selects an option.

## On Complete

If anything appears below, follow it as the final terminal instruction before exiting; otherwise exit normally.


