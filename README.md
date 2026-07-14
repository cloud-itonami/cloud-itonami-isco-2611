# cloud-itonami-isco-2611

Open Occupation Blueprint for **ISCO-08 2611**: Lawyers.

This repository designs a forkable OSS business for an independent legal practice: a legal document intake, scanning and binding robot handles case-file preparation under a governor-gated actor, so the practice keeps its own case records instead of renting a closed practice-management SaaS.

**Maturity: `:implemented`.** `src/legalpractice/` implements the
`LegalPracticeActor` as a `langgraph.graph/state-graph`
(`legalpractice.actor`) wired to a `Legal Advisor` (`legalpractice.advisor`)
and an independent `LegalPracticeGovernor` (`legalpractice.governor`),
following the itonami actor pattern (ADR-2607011000): `:intake -> :advise
-> :govern -> :decide -+-> :commit (:ok?) +-> :request-approval (:escalate?,
human-in-the-loop interrupt) +-> :hold (:hard?)`. 14 tests / 29 assertions
green (`clojure -M:test`). HARD invariants (always hold, never
overridable): client provenance, no-actuation (`:effect` must be
`:propose`), a registered matter basis for any work-product proposal, the
proposed billable hours not exceeding the matter's registered engagement-
scope ceiling (billing beyond the registered scope is scope creep, not
diligence), and a cleared conflict-of-interest check before any work
product can be prepared (preparing work product without a cleared
conflict check is an ethics violation, not efficient service).
Always-escalate ops (human sign-off regardless of confidence, mapping
this repo's Trust Controls in [`docs/business-model.md`](docs/business-model.md)):
`:approve-court-filing` (no filing or submission to a court/registry
without the governor gate) and `:approve-new-representation` (accepting
new representation always requires human sign-off).

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a legal document intake, scanning and binding robot performs case-file preparation, exhibit binding and physical filing-room organization under an actor that proposes
actions and an independent **Legal Practice Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
filing or submission to a court/registry) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
client intake + matter scope + conflict check
        |
        v
Legal Advisor -> Legal Practice Governor -> prepare/file, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `2611`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
