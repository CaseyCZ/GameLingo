# Pre-0.5.1 targeted review

Base: 6d1349c1bf44d70840948a24ee2f53cd31d311ba. Agency Roster reference: ziri22/agency-roster@bc5384b2eb92ad37657e3cc395447c272737ec2a. Reviewed guidance: code-reviewer, swift-specialist, kotlin-specialist, concurrency-specialist, security-auditor, test-engineer, ci-cd-pipeline, dogfood-testing-v4. Used as review checklists only, no external AI service.

## Fixed
- Standalone TV: after two blank OCR observations, discard the shown-source marker along with the candidate. Otherwise a line that returns before the 1.8 s timeout is suppressed because it still equals shown. The currently visible caption and original reading deadline remain untouched unless opt-in hide-on-source-gone is enabled.
- iOS yellow lane: retain two-letter choices (NO, OK, NE) just like Android, while still rejecting one-letter noise. Apply existing optional filters independently. Blue dialogue thresholds unchanged.

## Verification
- Added Swift behavior checks for short choices and noise, plus Python wiring regression assertions for both fixes.
- Prior base CI passed 8/8 jobs. A fresh candidate CI is required for this commit. Physical iPhone/Xbox/TCL testing, full security audit and measured performance are not established by this focused source review.
- Release unchanged. No new dependencies or build tools introduced.
