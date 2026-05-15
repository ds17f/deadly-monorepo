# Architectural Decision Records

This directory holds ADRs (Architectural Decision Records) — short documents
that capture significant decisions about how the codebase is structured, why
the alternatives were rejected, and what we expect to live with as a result.

## When to write one

Write an ADR when a decision is:

- **Hard to undo** once shipped (database schema, public API surface,
  platform choice, project-wide convention).
- **Cross-cutting** — affects multiple areas of the codebase or multiple
  team members.
- **Non-obvious** — someone reading the code six months from now would
  reasonably ask "why this and not the other thing?"
- **Worth defending** later when someone proposes changing it.

Don't write an ADR for routine code changes, bug fixes, or local refactors.
PR descriptions cover those.

## Format

Each ADR is a single markdown file named `NNNN-short-title.md`, where
`NNNN` is a four-digit zero-padded sequence number. Use the template
below.

```markdown
# ADR-NNNN: Short title

## Status

Proposed | Accepted | Deprecated | Superseded by ADR-XXXX

## Context

What's the problem? What forces are at play? What was tried before? Keep
it factual — describe the situation, not the decision.

## Decision

What we decided to do. Present tense. Specific. The actual choice, not
the reasoning.

## Consequences

What follows from this decision. Both the good (what we gain) and the bad
(what we accept, what we give up, what we'll have to live with).

## Alternatives considered

What else we looked at. Why those were rejected. One paragraph per option
is usually enough.
```

## Numbering

ADRs are numbered sequentially in creation order. Once accepted, don't
renumber — if a decision is overturned, write a new ADR with status
"Supersedes ADR-NNNN" and update the old one's status to "Superseded by
ADR-XXXX".

## Where ADRs sit relative to PLANS/

- **ADR** — the decision and its trade-offs. Stable. Rarely edited
  after acceptance.
- **PLANS/** — execution plans for specific work items. Living
  documents. Tied to Linear tickets. Get archived when the work ships.

An ADR may reference plans; plans should reference the ADRs that bind
them.
