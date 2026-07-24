# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]

**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]

**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]

**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]

**Project Type**: [e.g., library/cli/web-service/mobile-app/compiler/desktop-app or NEEDS CLARIFICATION]

**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]

**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]

**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Mark each gate PASS / FAIL / N-A. Any FAIL must be justified in Complexity Tracking below
or the plan must change. See `.specify/memory/constitution.md`.

| # | Gate | Status | Notes |
|---|------|--------|-------|
| I | Every planned capability traces to a user story / `FR-###` | | |
| II | Pipeline order respected: video → keypoints → sequence model → gloss+confidence → text/speech; LLM only gloss→sentence | | |
| III | Recognition consumes keypoints only, never pixels; no image filters feeding the classifier | | |
| IV | Keypoint data contract unchanged (63+63+75 = 201, shoulder-midpoint centering, z uncentered, linspace fixed-length); contract tests cover every producer | | |
| V | Reportable metrics use per-subject split; no regression below 0.85 accuracy (LSA64, subject 10 held-out) | | |
| VI | Each experiment records config, seed, dataset, dependency versions; artifacts stored separately | | |
| VII | Raw video never leaves the device; only keypoints cross the network | | |
| VIII | Confidence threshold configurable; below-threshold path says "no entendí" instead of guessing; UI states assistance-not-interpreter | | |
| IX | Perceived latency < 2 s; any compromise justified in `research.md` | | |
| X | LSA64 non-commercial license and attribution preserved | | |
| XI | Capture / keypoints / classification / gloss→text / presentation stay separately contracted | | |
| XII | Plan does not worsen inherited POC debt (Tasks API migration, temporal segmentation, per-subject CV, augmentation) | | |
| XIII | Business/pipeline rules have tests; data-contract tests wired as blocking CI | | |
| XIV | No production code written during specify/clarify/plan/tasks/checklist phases | | |

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
