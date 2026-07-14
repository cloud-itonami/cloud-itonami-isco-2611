# cloud-itonami-isco-2611

Open Occupation Blueprint for **ISCO-08 2611**: Lawyers.

This repository designs a forkable OSS business for an independent legal practice: a legal document intake, scanning and binding robot handles case-file preparation under a governor-gated actor, so the practice keeps its own case records instead of renting a closed practice-management SaaS.

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
