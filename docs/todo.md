# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section.

Current focus: **§3.20 — recalibration**, which **needs a DESIGN PASS first**.
Phase 3's tail is **§3.18 steals (SHIPPED) → §3.19 instrumentation (SHIPPED) → §3.20
recalibration**.
⚠ **RECALIBRATION HAS BEEN RENUMBERED A FOURTH TIME** — it was §3.16, then §3.18, then
§3.19, and is now **§3.20**. **Read the phase NAME, never the number alone.**
§3.7–§3.19 have all shipped.

> **§3.20 NEEDS ITS OWN DESIGN SESSION, AND IT OPENS WITH A MEASUREMENT RATHER THAN WITH
> QUESTIONS.** That measurement is done: **§3.19's 5-seed run (seeds 1000–5000) is
> recorded in [calibration.md](calibration.md)'s Current column** and is this pass's
> first input. **This file has NOT been rewritten for §3.20** — that is the design
> session's first job, and its content lives in **roadmap.md's §3.20 bullet** in the
> meantime (see the pointer section below).
>
> ✅ **§3.19 (instrumentation) SHIPPED 2026-08 — no `#NNN`, because it resolved no design
> question.** All three tasks landed as planned, and **the phase moved no engine number**
> (same-seed harness run before/after, identical line for line). The full landing note is
> on **roadmap.md's §3.19 bullet**; what §3.20 needs to know from it is short:
> - **The harness now self-verifies THREE identities**, all reading `OK` on all five
>   seeds: `ast+blk` (pre-existing), **`ft-src`** (per-source FT counts sum to total FTs
>   **and** the `UNKNOWN` bucket is empty) and **`points`** (events = box score = final
>   score). ⚠ **A `MISMATCH(es)` on any of them invalidates the rows it feeds — fix the
>   instrument before reading a number.** That is the whole point of the phase: a wrong
>   reading looks like a calibration gap, so you tune a constant to chase a measurement
>   error.
> - **The seeded tests are trustworthy now.** The technicals test asserts its identity
>   over ten seeds at the realistic 25 possessions/period with the coin-flip precondition
>   deleted, so **no §3.20 change can break it by shifting the RNG stream**. And
>   `V1ApiDelegateimplTest` is `@Transactional`, so **a green `mvn clean install` finally
>   does mean the sim tests pass in isolation** — run them alone anyway, it is cheap.
> - **Some calibration.md rows moved against the previous reading. They were STALE, not
>   new** (4/5/6 fouls 0.94/0.46/0.30, and-1s 1.55, fouls/period 4.58, OOB 3.1, ejections
>   0.033). ⚠ Do not read them as drift and do not tune toward the old values.

---

## §3.20 (recalibration) — the NEXT phase. Pointers only, until its design pass rewrites this file.

✅ **The precondition is met: §3.19 has shipped and its 5-seed run is recorded** in
[calibration.md](calibration.md)'s Current column. That run is §3.20's first input.
⚠ **This section is deliberately thin, and that is not an omission.** The content lives
in **roadmap.md's §3.20 bullet** so it survives this file's rewrite — todo.md is
current-phase-only, and **rewriting it for §3.20 is that design pass's first job**.

**When you get there, read in this order:**
1. **[roadmap.md](roadmap.md)'s §3.20 bullet** — the core problem (**2P%, FTA and points
   are over-determined**: ~+9.8 points of lift available against a 5.6-point gap, so they
   cannot all be hit independently), the levers, the frozen decisions, the exit condition.
2. **[calibration.md](calibration.md)** — live numbers and the operative rules.
3. **[decisions.md](decisions.md)** — the **"⚠ WHAT §3.20 INHERITS"** handoff block at the
   end: every gap, the `#NNN` that owns it, and the constraint on each that is easy to
   miss. An index over #036 / #039 / #040 / #041, so you need not read five entries.

---

## ⚠ Traps that bite EVERY session — read these before trusting a doc

**1. SUB-PHASE NUMBERS HAVE BEEN REUSED — FOUR TIMES FOR ONE PASS.** Recalibration was
§3.16, then §3.18, then §3.19, and is now **§3.20**. ⚠ **So BOTH numbers are ambiguous:**
every **"§3.16"** written before 2026-08 means RECALIBRATION (the §3.16 slot became
shooting-foul composition, shipped), and every **"§3.19"** written before this session
also means RECALIBRATION (the §3.19 slot is now **instrumentation** — this phase).
⚠ **The docs have NOT been swept**: ~60 stale "§3.19 = recalibration" references remain
in `decisions.md`, `roadmap.md`, `backlog.md` and `ideas.md`, left deliberately rather
than mass-edited, because retro-editing history is worse than a callout. **Read the phase
NAME, never the number alone.** roadmap.md carries the mapping.

**2. A NUMBER CAN MOVE — OR FAIL TO MOVE — FOR REASONS THAT ARE NOT THE ENGINE.**
⚠ **This is §3.20's single most important trap, because recalibration's whole job is
moving numbers** — but it applies here too: **§3.19 must move NO number**, so the same
question catches a test-side change that unexpectedly did. Before tuning anything, ask: **did the engine change, did the MEASUREMENT
change, or is a clamp holding it?** All three have happened:
- **The instrument broke** — a harness row classified a stopped shot by its free-throw
  count, and a phase that changed what a foul awards silently halved the row.
- **⚠ FIXING an instrument also moves numbers, and looks like a regression** — `Stopped
  shots / team` read 12.43 post-fix against 7.65 pre and appeared to double. **It fell**
  (7.39 → 6.24 like-for-like). **Compare raw-to-raw across an instrument change.**
- **⚠ A CLAMP can do what a constant appears to do, and the tell is a number that DOESN'T
  move** — blocks were predicted to fall to ~2.6 and stayed at 4.80. See Q1.

**3. THE HARNESS NEEDS ITS PROFILE IN THE ENVIRONMENT.** `-Dspring.profiles.active` does
**not** reach the forked Surefire JVM; the context comes up with `activeProfiles = []`
and fails with what looks like a database error. Use
`SPRING_PROFILES_ACTIVE=local,baseline`, run from `gametime-service/`, and **confirm the
report's `Profiles:` line before trusting any number.** This has cost two sessions.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved out
so this file can be rewritten each phase without losing it. **This is a ROUTING TABLE —
pointers only.** The reasoning lives at the destination; do not restate it here, and do
not add "done" entries (those are the `#NNN` entry's job).

**Deferred engine work — see the cited entry's follow-up list:**

- **Technicals** — bench/coach technicals; a technical's lack of causal input → **#032**
- **Foul trouble** — period-/time-awareness (#031 C); getting foul-outs below ~0.38
  (lever measured saturated); splitting `substitutionAggressiveness` (#031 B) → **#031**
- **Over-dispersion** — `pickDefender` concentration also reaches blocks, steals and
  shot contests → **#031 A**
- **Seams left open by their design pass** → §3.6 **#024** · §3.7 **#025** · §3.9
  **#027** · §3.10/§3.11 **#028/#029** (incl. `committing_team_id` not on the OpenAPI
  `GameEvent` — ✅ **CLOSED by §3.18**) · §3.12 **#030** (~20 3PA/team/game
  — ✅ **CLOSED by §3.17**, landed 37.22) · §3.16 **#039** (the unsourced shooting-foul
  share — ⚠ now also **mispriced**, see #040) · **§3.17 #040** (FG%/2P%, FGA, FTA and
  the `PROB_FLOOR`-vs-`base-block-three` finding — all **§3.20's**)

**Other files own these outright:**

- **The phase sequence, this phase's bullet, and §3.20 (recalibration) — including its
  core over-determination problem** → [roadmap.md](roadmap.md)
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores**, and **the `decisions.md` condense pass** (a
  **gate on starting Phase 4**) → [backlog.md](backlog.md)
- **Untriaged ideas**, incl. the parked cap 3→5 tuning idea and strategic substitution →
  [ideas.md](ideas.md)

**Standing facts (true every phase, not deferrals):**

- `CalibrationHarness` is disabled by default and lives in the `sim` test sources; run
  it with `-Dcalibration=true -DcalibrationSeed=NNNN`. **Re-run after any `SimConfig`
  change.** Since §3.15 the sim profile is selected by the ordinary Spring profile list —
  `SPRING_PROFILES_ACTIVE=local,baseline` (#035 F — ⚠ **the `-D` form does NOT reach the
  forked surefire JVM**). ⚠ **`baseline` must always
  be in the list** (it carries all **62** values), and **order is positional** — putting it
  last lets it win and silently neutralizes the era file. The **effective-config dump**
  in the report is what catches that, and it also catches a caller passing its own copy
  of a profilable value (the §3.15 harness-pace trap).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B) — though §3.15 makes it *profilable* (#035 C).
