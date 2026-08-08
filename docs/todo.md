# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.13).

Current focus: **§3.12 — All-shot-type contact fouls + graduated foul rate +
fouled-three = 3 FTs**. The full §3.7–§3.13 sequence, the
calibration-blast-radius ordering, and what follows (Phase 4) live in
**roadmap.md's "Possession-fidelity completion" section** — not here (todo.md is
current-phase-only). §3.7–§3.11 have all shipped; §3.12 is next, then §3.13
(flagrants/technicals).

> **§3.12 is EXECUTE-READY — the design pass is resolved as `decisions.md #030`**
> (Decisions A1/A2/B/C/D/E/F/G). Build the plan below; do not re-litigate the calls.
>
> The shape: today the **entire foul model** gates on the binary
> `ShotType.isContactType()` (`DRIVE || POST`). §3.12 **deletes** that binary (#030
> A1) for a **graduated per-shot-type foul multiplier** so *every* shot type can
> draw a foul and an and-1 (post/drive frequent → perimeter uncommon → three rare —
> a closeout on a three-point shooter is a real foul). The same root cause carries
> the **latent fouled-three bug**: a shooting foul on a `THREE` must award **3** free
> throws, and today it never fires only because `THREE` isn't a contact type — so
> widening contact **activates** the bug and must fix it in the same pass (#030 C).
> §3.11's per-situation FT count (#029 D) is the seam: pass `3`.
>
> **This one moves scoring twice** (more fouls, and more 3-FT trips), and it also
> inherits **§3.11's deliberately deferred re-centering** — points sit at **115.9**
> vs. the ~112 §3.4 target because §3.11 took no shot-`BASE_*` trim. §3.12
> re-centers **once**, from that 115.9 baseline (#030 E — and risks.md's live
> failure mode is "§3.12 forgets to absorb it"). **Read #030 in full, plus #029's
> and #028's implementation notes, before touching a resolver.**

---

## §3.12 execution plan (decisions.md #030 A1/A2/B–G — resolved, ready to build)

Build order. Seam: a **per-shot-type foul-multiplier table** in `SimConfig` +
`ShotType` (delete `isContactType()`, add `freeThrowsIfFouled()`) + `FoulResolver`
(`isFoul` multiplies instead of early-returning; `isAndOne` gains a `ShotType`) +
`PossessionEngine` (the and-1 gate **removed**, the pre-shot FT count now per-type) +
`CalibrationHarness`, plus a mechanical `BASE_FOUL` → `BASE_NO_BASKET_FOUL` rename
(Step 0, #030 F — value unchanged). **No schema, no OpenAPI, no new `PlayType`, no new
`FreeThrowSource`, no new `outcome` string** — a fouled three emits the existing
`SHOOTING_FOUL` and three existing `MADE_SHOOTING`/`MISSED_SHOOTING` events. Mirror
the §3.7–§3.11 execution rhythm.

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. Engine work in the `sim` package —
> no OpenAPI/schema change.

**The two commands you need** (both verified working, 2026-08 — run from the repo root):

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install
```

The first runs the harness (~30s, 102 games) — it is `@EnabledIfSystemProperty`
disabled without `-Dcalibration=true`, so it never runs in a normal build. Vary
`-DcalibrationSeed` for the multi-seed discipline (#029 E). The second is the full
gate including JaCoCo coverage.

**Measured pre-§3.12 baseline (seed 1000, 2026-08 — verified by actually running it,
so these are real numbers, not estimates):**

| | |
|---|---|
| Points / FG% / 3P% | **115.3** / 47.0% / 37.4% |
| FGA / **3PA** per team/game | 89.8 / **20.3** ← well under the NBA's ~35; see the Step 5 note |
| Fouls / team / game | **16.8** total (12.89 `SHOOTING`, 1.61 `AND_ONE`, 1.84+0.48 rebounding) |
| Fouls / team / period · bonus | 4.37 · 43.1% of team-periods |
| And-1s | 1.61/team/game (6.2% of made contact FG) |
| FTA / team / game | 28.2 (SHOOTING 91.3% / AND_ONE 5.7% / BONUS 3.0%) |

*(Single seed — §3.11's 5-seed mean was 115.9 points, so expect ±1.5 per-seed noise.
Use the mean, never one run, when tuning.)*

**Step 0 — rename `BASE_FOUL` → `BASE_NO_BASKET_FOUL` (#030 F).**
- [ ] Pure rename — **the value stays `0.15`, the meaning is unchanged.**
  **`BASE_FOUL` does not survive this phase**: it is a rename, not an alias or a
  deprecation, so there is no transition period and no second constant to keep in
  sync. After Step 0 the identifier `BASE_FOUL` exists nowhere in the codebase, and
  any later reference to it (in #028/#029, or in the prose of #030 itself) is
  **historical** — the same constant under its pre-§3.12 name.
  **Confirmed against the code (2026-08), it is exactly 5 references in 2 files, and
  ZERO in tests** — so this is a small, fully-enumerable change:
  - `SimConfig.java` **×4** — the declaration (`:134`), two class-javadoc mentions
    in the §3.10/§3.11 tuning findings (`:57` the wrong-way-lever bullet, `:95` the
    §3.11 "not touched" bullet), and one inline comment (`:278`).
  - `FoulResolver.java` **×1** — the `contestProbability` call in `isFoul` (`:41`).
  - **No test references it by name** (verified) — so no test churn from the rename
    itself, only from the behavior changes in Steps 1–4.
- [ ] While renaming `:57`/`:95`, keep the wrong-way-lever explanation intact — it
  is the finding that justifies the new name, and #030 F leans on it.
- [ ] **Do this as its own mechanical commit before Step 1** if you want a readable
  §3.12 diff — a rename mixed into a behavior change is hard to review (that is the
  only open call in F).
- [ ] **Do NOT rename the `SHOOTING_FOUL` `outcome` string** — that is persisted
  event vocabulary, and §3.12 changes no outcome strings.
- [ ] Why: `BASE_FOUL` reads as "the foul rate" and is actually P(a foul **stopped
  the shot**) — the possession-ending branch. That gap is what invited using it as a
  points lever twice (#028's finding, #029's re-warning). After Step 2 deletes the
  gate that was implicitly disambiguating it, **two** foul rates apply to all four
  shot types (`BASE_NO_BASKET_FOUL` = the shot stopped; `AND_ONE_BASE` = the shot
  survived), so the pair must name their outcomes. "No basket" is basketball's own
  term and pairs against the and-1.

**Step 1 — the per-shot-type foul-multiplier table (#030 A1/A2).**
- [ ] Add a per-shot-type foul multiplier to `SimConfig` — the `BASE_BLOCK_*` shape
  (§3.7 / #025 C). Leading shape: four constants (`FOUL_MULT_DRIVE`, `_POST`,
  `_PERIMETER`, `_THREE`) plus a small `foulMultiplier(ShotType)` lookup. Exact
  shape is open-at-execution, **constrained**: the numbers stay in `SimConfig` (the
  tuning surface) — do not put them on the enum.
- [ ] **`DRIVE = 1.0` is the anchor and `BASE_NO_BASKET_FOUL` stays `0.15`,
  untouched** (A2 — the rename in Step 0 changes the label only).
  `POST` must preserve today's behavior too — both types simply passed the same
  gate today, so both are `1.0` unless the harness argues otherwise.
- [ ] `PERIMETER ≈ 0.35`, `THREE ≈ 0.133` — **placeholders**, settled by the harness
  (Step 5). Post/drive frequent → perimeter uncommon → three rare.
- [ ] **`THREE = 0.133` ⇒ a 2% foul rate on threes — AGREED with the user
  (2026-08), not an open question.** The multiplier maps to a foul rate as
  `0.15 × mult`:
  | `FOUL_MULT_THREE` | % of 3PA fouled | 3-FT trips/team/game* |
  |---|---|---|
  | **0.133 ← agreed** | **2.0%** (~1 in 50) | **~0.40** |
  | 0.20 (earlier draft) | 3.0% (~1 in 33) | ~0.61 |
  | 0.33 | 5.0% (~1 in 20) | ~1.00 (well above the real rate) |
  *\*Trip counts computed off this engine's **measured 20.3 3PA/team/game** (seed
  1000, 2026-08) — **not** the NBA's ~35, so they read lower than the real-league
  ~0.7 at the same rate. **Anchor on the RATE (2% of 3PA), not the count.***
  **Why 2% and not 3% — the two measures are different, and 2% is the right one
  to anchor on.** Real NBA runs ~35 3PA/team/game against ~0.7 fouled-three
  (3-FT) trips ⇒ **~2% of 3PA are STOPPED-and-fouled**; the ~3% figure counts
  **any** foul on a 3PA, and-1s included. Since this multiplier's most consequential
  output is the **3-FT trip** (the highest-yield event in the model, ~2.25 pts vs.
  the ~1.01 3PA it replaces), anchoring on the stopped-three rate is the more
  faithful calibration. The and-1 threes it also governs ride along on top.
  *(A 5% rate — mult ~0.33 — was considered and set aside: well above the real rate,
  and it would add roughly +0.6 pts/team/game on a pass already 3.9 over target,
  spending §3.12's cheapest lever on realism that must then be bought back with
  calibrated FG%.)*
  Still verify on the harness (Step 5): if the 3-FT trips line lands far outside
  **~0.3–0.6**, the multiplier — not the benchmark — is what moves.
- [ ] **`PERIMETER = 0.35` ⇒ ~5.25% (≈1 in 19) — the LEAST-grounded of the four,
  flagged so it is not mistaken for a derived number.** Unlike `THREE` (anchored to
  a real ~0.7 trips/game benchmark) and `DRIVE`/`POST` (pinned at 1.0 to preserve
  §3.11 behavior), this one was chosen by feel — it only had to sit between the
  drive and the three. A perimeter shot here is a **mid-range two** (pull-ups,
  turnarounds, elbow jumpers), which draws fouls more than a three (closer to the
  defender, more contact) but far less than a drive.
  **Expected landing zone: `0.25`–`0.40` (user, 2026-08)** — start at 0.35 but
  expect to settle **nearer the bottom of that band**. Read it as a **ratio to the
  three**, which is the one value here that *is* anchored (2% of 3PA):
  | mult | rate | vs. a three | fouls on perimeter/team/game |
  |---|---|---|---|
  | 0.25 | 3.75% | 1.9× | ~0.9 |
  | 0.30 | 4.50% | 2.2× | ~1.1 |
  | 0.35 (start) | 5.25% | 2.6× | ~1.3 |
  | 0.40 | 6.00% | 3.0× | ~1.5 |
  A mid-range jumper fouled **2–3× as often as a three** is defensible; above ~3×
  reads high for a jump shot, below ~1.5× would make mid-range nearly as foul-free
  as a spot-up three. *(Counts assume ~25 perimeter attempts/team/game off the
  measured 89.8 FGA − 20.3 3PA — confirm against the per-shot-type line rather than
  trusting the assumption.)*
  **Lower stakes than `THREE`**: a fouled perimeter awards 2 FTs (~1.5 pts) against
  the ~0.88 it removes — **~+0.6 net, about half the fouled-three's yield**.
  **⚠ Do NOT tune `PERIMETER` to fix points.** It is a **realism** knob whose right
  value is independent of the points total; if §3.12 lands high, the sanctioned
  lever is the shot `BASE_*` (E), not whichever multiplier moves points most. Set
  perimeter where mid-range fouling *looks right*, then re-center separately — the
  same discipline the `THREE` decision followed.
- [ ] **These four constants ARE the per-shot-type foul knobs** — one per type, and
  the only sanctioned way to make a shot type foul more or less.
  > **⚠ A `FOUL_MULT_*` value is a MULTIPLIER, not a probability.** `FOUL_MULT_THREE
  > = 0.133` does **not** mean "a three fouls 13.3% of the time" — it means "a three
  > fouls at **13.3% of the rate a drive does**", i.e. `0.15 × 0.133 = 0.02` = **2%**.
  > These sit next to `BASE_NO_BASKET_FOUL = 0.15` in `SimConfig` and every value
  > looks like a probability, so **say the units out loud in the javadoc** and name
  > the constants so a tuner cannot misread them (this has already caused one
  > real misunderstanding during the design pass).
  The arithmetic is `P(foul) = BASE_NO_BASKET_FOUL × FOUL_MULT_<type>` (then the
  usual skill/fatigue/`defensivePressure` terms), so at the placeholders:
  | Shot type | Multiplier (× drive rate) | **Actual foul probability** |
  |---|---|---|
  | `DRIVE` | **1.0** (anchor) | **15%** — unchanged from §3.11 |
  | `POST` | **1.0** | **15%** — unchanged from §3.11 |
  | `PERIMETER` | ~0.35 | **~5.25%** |
  | `THREE` | **~0.133** | **~2%** ← agreed with the user (2026-08) |
  Three properties to know before turning them: **(a)** each knob drives **both**
  foul types for that shot type — the stopped-shot roll *and* the and-1 roll (B,
  one shared table); there is no per-type way to tune those separately today.
  **(b)** Because the multiplier therefore lands **twice** in the and-1 path, these
  knobs are **non-linear on and-1s** — halving `FOUL_MULT_THREE` more than halves
  the three's and-1 rate. **(c)** `DRIVE = 1.0` is only a reference point, not a
  mechanically privileged value, but **raising it breaks A2's attributability**
  (drive/post bit-identical to §3.11, which is what makes §3.12's delta
  measurable) — if drives should foul more, that is a `BASE_NO_BASKET_FOUL`
  conversation, and it reopens a §3.4-calibrated number.
- [ ] Javadoc the anchor semantics: the multiplier is **relative to `BASE_NO_BASKET_FOUL`'s
  drive rate**, which is why drive/post behavior is bit-identical to §3.11 and the
  whole §3.12 delta is attributable to perimeter/three (A2).
- [ ] Note in the javadoc (#029's measured finding, #030 A1): a multiplier of
  **`0.0` is the only true off-switch** — it scales the whole probability including
  the skill term, unlike a zero *base*, which leaves a skill-driven tail.

**Step 2 — `ShotType`: delete the binary, add the FT-count rule (#030 A1/C).**
- [ ] **Delete `ShotType.isContactType()` outright.** No convenience predicate
  survives (A1) — a leftover binary beside a graduated table is a second source of
  truth (#013/#015). All three call sites are handled in Steps 3–4.
- [ ] Add **`ShotType.freeThrowsIfFouled()`** returning **3** for `THREE`, **2** for
  `DRIVE`/`POST`/`PERIMETER` (C). This is a **rule**, not a tunable — it lives on
  the enum, deliberately **not** in `SimConfig` (which is the tuning surface, and a
  rule housed there invites someone to tune it).

**Step 3 — `FoulResolver`: both rolls read the same table (#030 A1/B).**
- [ ] `isFoul(shotType, …)` — **remove the `if (!shotType.isContactType()) return
  false;` early return** and instead multiply the computed probability by
  `config.foulMultiplier(shotType)`. Everything else (the avg-10 `foulDrawing` vs.
  `SCALE_AVG*2 - foulProne` wiring, fatigue, `defensivePressure`, the
  `clampProbability` call) is **unchanged**.
- [ ] `isAndOne(...)` — **add a `ShotType` parameter** and multiply by the **same**
  `foulMultiplier(shotType)` (B — one shared curve, not two tables). Its
  `AND_ONE_BASE` + `AND_ONE_SENSITIVITY` + `rareEventProbability` wiring is
  **unchanged**.
- [ ] Do **not** add a second, and-1-specific multiplier table. An and-1 on a three
  being rarer than a foul on a three **already falls out** of the two rolls being
  independent (the shot must also go in) — modeling it again double-counts it (B).

**Step 4 — `PossessionEngine`: drop the and-1 gate, pass the per-type FT count (#030 A1/C).**
- [ ] Pre-shot foul branch (`// 2. Foul check`, ~L130–144): pass
  **`shotType.freeThrowsIfFouled()`** where it passes `SimConfig.FREE_THROWS_PER_FOUL`
  today (~L141). Source stays `FreeThrowSource.SHOOTING` — **no new source** (C: the
  source says *why*; three-FT-ness is carried by the count).
- [ ] Fix the now-false comment on **L129**: `// 2. Foul check (drive/post only)` —
  it is no longer drive/post only. (Small, but it is the one line in the engine that
  still asserts the deleted gate in prose.)
- [ ] And-1 block (~L213): **delete the `shotType.isContactType() &&` gate** — every
  made shot now rolls the and-1 — and pass `shotType` into `isAndOne(...)`.
- [ ] `awardAndOne(...)` keeps `SimConfig.AND_ONE_FREE_THROWS = 1` for **every** shot
  type, **including a made three** (C). A made 3 + foul = 3 points + **1** FT.
  **Do not** make this count graduate in parallel with Step 2's — it is a
  one-character mistake the `count` parameter makes easy, and it silently inflates
  scoring on top of §3.12's real lift. Guarded by a test (Step 6).
- [ ] `SimConfig.FREE_THROWS_PER_FOUL = 2` **survives unchanged** as the §3.10 bonus
  (non-shooting) count — that call site (~L333) is untouched.

**Step 5 — harness + the SINGLE re-centering (#030 D/E).**

> **Do these in order — the sequence is load-bearing.** Several items below are
> prerequisites for the ones after them, and running them out of order produces
> confident numbers that are wrong:
> 1. **Fix the instruments** (and-1 denominator, add the foul-out line, per-shot-type
>    breakdown) — *before reading a single §3.12 number*. A broken instrument in the
>    direction of the change is worse than no instrument.
> 2. **Baseline**: run the harness with the §3.12 code in place but **before** any
>    tuning, across **several seeds**, and record the untuned landing.
> 3. **Decompose the lift by channel** (stopped-shot / and-1 / possession-ending)
>    and compare against the priors below. *Do not pick a trim before this.*
> 4. **Tune, cheapest lever first** — the new `PERIMETER`/`THREE` multipliers, then
>    (only for the remainder) the shot `BASE_*`. Never `BASE_NO_BASKET_FOUL`.
> 5. **Re-agree the landing with the user**, including the FG%/3P% cost of any
>    `BASE_*` trim.

- [ ] **Confirm, don't rebuild, the foul instrument (D):** the §3.10/§3.11 lines
  already print fouls/team/period and team-periods-in-the-penalty
  (`CalibrationHarness:388–392`). Verify they do, then read them — **no new harness
  line is needed for the penalty question**.
- [ ] **FIX THE AND-1 DENOMINATOR FIRST — the instrument breaks silently otherwise.**
  `CalibrationHarness:223–227` counts `madeContactShots` as
  `startsWith("MADE") && (endsWith("DRIVE") || endsWith("POST"))`, and line 415
  prints and-1s as a **% of made contact FG**. §3.12 makes the **numerator** count
  and-1s on *all four* shot types while that **denominator** still counts only two —
  so the printed percentage inflates and the §3.11 comparison number (6.4%) becomes
  meaningless. **Widen the denominator to all made FG** and re-label the two lines
  (`Made contact FG` → `Made FG`; drop "the and-1 denominator: made DRIVE/POST").
  Do this **before** reading any §3.12 number off the harness.
- [ ] Note the consequence for comparison: §3.11's **6.4% of made contact FG** is
  measured against the *old, narrower* denominator, so it is **not** directly
  comparable to §3.12's percentage. Compare **and-1s per team per game** (1.67) for
  continuity across the change, and treat the new percentage as a fresh baseline.
- [ ] **ADD A FOUL-OUT LINE — this is a genuinely new instrument, and §3.12 needs
  it** (#030 G). Nothing in the harness measures foul-outs today: the mechanism
  exists (`SimConfig.FOUL_OUT_LIMIT = 6`, `PlayerGameState.hasFouledOut()`, §3.5
  forces the player off) but its rate has **never been observed**. §3.12 raises
  fouls across all five defenders, so this is the pass where it moves. Print:
  **foul-outs per team per game**, and the **distribution of per-player fouls** at
  game end (how many players finish with 4, 5, 6) — the distribution is what shows
  a problem *before* it reaches the 6 threshold.
- [ ] **Plausibility benchmarks for the two foul lines (judge against these, not
  intuition):**
  | Measure | Real-NBA ballpark | §3.11 shipped | Verdict at §3.12 |
  |---|---|---|---|
  | Fouls / team / game | ~19–20 | **16.8 measured** (seed 1000: 12.89 SHOOTING + 1.61 AND_ONE + 2.32 rebounding) | should rise toward ~19–20, **not** past it |
  | Foul-outs / team / game | **~0.11** (≈1 per 9 games) | **unmeasured** | if >~0.3, the rate is too high |
  | Fouled-three trips / team / game | **~0.7** real (off ~35 NBA 3PA); **~0.40 expected here** at `THREE = 0.133` | 0 (impossible today) | see the note below — do **not** chase 0.7 |
  These are ballparks for judging plausibility, **not** new hard calibration targets
  — the §3.4 five (~112/47/36/26/14) remain the targets. If fouls/team/game lands
  near ~20 **and** foul-outs stay near ~0.1, the multipliers are about right.
  **⚠ On the fouled-three row — expect ~0.40 here, NOT 0.7, and the gap is
  EXPECTED for two compounding reasons.** (1) ~0.7 is the real-NBA count for *all*
  fouls on threes, whereas `THREE = 0.133` deliberately encodes the **stopped-three**
  rate (~2% of 3PA — Step 1). (2) **This engine shoots 20.3 3PA/team/game, not the
  NBA's ~35** (measured, seed 1000, 2026-08), so an identical *rate* necessarily
  yields fewer *trips*. 2% × 20.3 ⇒ **~0.40 trips/team/game**. A builder who
  "fixes" 0.40 up to 0.7 would silently undo the user-agreed multiplier and add
  ~+0.35 pts/team/game. **Judge the RATE (~2% of 3PA), not the count.** Only if
  the harness lands far outside ~0.3–0.6 trips is the multiplier itself wrong.
  *(That the engine shoots well under the NBA's 3PA volume is a separate,
  pre-existing observation — not §3.12's to fix, but worth a note at close-out.)*
- [ ] **Break the foul lines down BY SHOT TYPE** (both the stopped-shot fouls and
  the and-1s). Without it the aggregate hides which multiplier is wrong — and the
  per-type figures above are an **analytic estimate over an assumed shot mix**, not
  a measurement (`PlayerGameState.shotTypeWeight` weights `DRIVE` as
  `drive + finishing` vs. a single skill for the other three, so drive is
  structurally over-weighted, and the coach shot-mix lean is ignored). The
  `SHOOTING_FOUL` event does not carry the shot type today, so derive it from the
  preceding `SHOT` event, or count fouled-threes by their **3-FT trips** (an FT
  triple tagged `SHOOTING` is unambiguous — no new event field needed).
- [ ] **Use the fouled-three rate to set the `THREE` multiplier directly.** It is
  the highest-yield event in the model (~2.25 expected points, vs. ~1.01 for the
  3PA it replaces), so its multiplier is the single number most able to distort
  scoring. Tune it against the ~0.7 trips/team/game benchmark rather than against
  the points aggregate alone — points can be re-centered by other levers, but a
  wrong fouled-three rate is a *realism* error no amount of `BASE_*` trimming fixes.
- [ ] Run the harness **before tuning** and **decompose the lift by channel** (E) —
  both prior foul passes proved the headline number hides the mechanism, and each
  found a *different* dominant channel. §3.12's three:
  - **Stopped-shot channel (up, and larger than it looks).** A stopped `PERIMETER`
    removes ~0.88 expected points and awards 2 FTs ≈ 1.5 → **net +~0.6**. A stopped
    `THREE` removes ~1.01 and awards **3** FTs ≈ 2.25 → **net +~1.2, the biggest
    per-event gain in the model.** (The pre-§3.12 brief guessed this channel might
    be a wash or negative — it is **not**; #030 E corrects it.)
    **Sized per game at the agreed multipliers:** the fouled-three sub-channel is
    ~0.40 trips × +1.2 ≈ **+0.48 pts/team/game** (computed off the **measured** 20.3
    3PA/team/game, not an assumed mix; it was ~+0.69 at the earlier `THREE = 0.20`,
    so the 0.20 → 0.133 trim already removes ~0.2 of §3.12's lift *before* Step 5
    spends a single lever). Perimeter fouls add their own, larger share on top —
    perimeter is both more frequent and more foul-prone than threes here.
    **Use these as the prior when decomposing** — if the harness's stopped-shot
    channel lands far outside ~+1 to +2 pts/team/game total, something in the
    multipliers or the shot mix is not what this plan assumed.
  - **And-1 channel (up, purely additive — but SMALLER than it looks).** Perimeter
    /three makes can now draw one, the §3.11 shape extended. **Expect only a ~1.2×
    rise in the and-1 rate even though the roll's reach roughly doubles** (51% →
    100% of attempts): the shared multiplier appears **twice** in the and-1 path —
    scaling both the stopped-shot roll the shot must survive *and* the and-1 roll
    itself — and it compounds with the lower make rates. Per attempt at the
    placeholders: **DRIVE ~2.79% / POST ~2.33% / PERIMETER ~0.80% / THREE ~0.36%**,
    i.e. a perimeter and-1 is ~3.5× rarer than a drive's, a three's ~8× rarer.
    Aggregate ~1.35% → ~1.62% of possessions. **Do not budget the recalibration
    against this channel** — analytic estimate, but the direction is solid.
  - **Possession-ending channel (down, secondary)** — a stopped attempt removes an
    offensive-rebound opportunity.
- [ ] **Re-center ONCE, from the 115.9 baseline, to ~112** — absorbing both §3.12's
  own lift and §3.11's deferred 3.9-point debt. This is the whole point (risks.md's
  live failure mode is re-centering only §3.12's own lift).
- [ ] **Spend the levers IN THIS ORDER (#030 E) — cheapest-first:**
  1. **The new `PERIMETER`/`THREE` foul multipliers (Step 1) — spend these FIRST.**
     They are **uncalibrated numbers invented by this pass**, so trimming them costs
     **nothing calibrated** — it only says jump-shooters draw fouls somewhat less
     often, and no measured target says otherwise. This lever did not exist in
     §3.10/§3.11, which is why those passes went straight to shot `BASE_*`.
  2. **The shot `BASE_*` rates — only if step 1 can't close the gap.** The proven
     lever (§3.10/§3.11; §3.7's nudge-up in reverse), but it **costs calibrated
     FG%** — ~0.6% FG% per 1.0 point at §3.11's measurement, so closing ~4 points
     naively would drag FG% from 46.8% toward ~44.4%, well off the ~47 target. Use
     sparingly and re-measure the exchange rate first (below).
  3. **`BASE_NO_BASKET_FOUL` — NEVER.** See the guardrail below.
- [ ] **Do NOT touch `BASE_NO_BASKET_FOUL` to remove points** — it is a **wrong-way lever**:
  trimming it *raises* points. **The mechanism:** it does not control
  scoring, it controls a **swap** — when it fires, a live shot is replaced by a
  2-FT trip worth ~1.5 points; when it doesn't, the possession keeps a live shot
  worth *more* than 1.5 once its offensive-rebound, and-1, and rebounding-foul
  continuations are counted. So a lower `BASE_NO_BASKET_FOUL` converts FT trips back into the
  more valuable live shots. Measured in #028: 0.15 → 0.138 moved points **up** to
  116.1. §3.12 makes this wrong-way effect apply to jump shots too.
- [ ] **Re-measure the FG%-per-point exchange rate on §3.12's own numbers** before
  trading any FG% away. §3.11's ~0.6%/point was measured on a **pure-FT** lift and
  does not carry over to a mixed one.
- [ ] Watch **3P% and FG%** — the pre-shot foul branch returns **before**
  `recordFieldGoalAttempt()`, so a stopped shot charges **no FGA** and removes
  points without moving FG% directly, **but** it changes the shot *mix* FG% is
  computed over (fouls now remove perimeter/three attempts, not just drive/post), so
  both can drift with no individual shot probability changing.
- [ ] **Steer by MULTIPLE `-DcalibrationSeed` runs, tuned to the MEAN** (#029 E) —
  per-seed noise is ±1.5 points, enough to bait an over-correction.
- [ ] If the **penalty rate** lands implausible: trim the `PERIMETER`/`THREE`
  multipliers (the uncalibrated new numbers). **Never** move
  `BONUS_FOULS_PER_PERIOD` — 5 is the real NBA rule, not a lever (D).
- [ ] Re-agree the final landing with the user against §3.11's
  (115.9 / 46.8% FG / 37.6% 3P / 27.5 ast / 13.9 TO / 4.9 blk / 2.8 OOB) and the
  ~112/47/36/26/14 §3.4 targets.
- [ ] **If ~112 cannot be reached without pulling FG% off its ~47 target: STOP at the
  best landing and bring the user the trade, don't force the number.** This is a
  live possibility, not a hypothetical — it is exactly the call §3.10 made (landed
  113.8, not 112.9, because "trading a calibrated number for an uncalibrated one is
  a bad swap") and §3.11 made (took no trim at all). **Points is one target among
  five; FG%/3P% are equally calibrated.** Present: the landing each candidate trim
  reaches, what it costs in FG%/3P%, and a recommendation. The user picks.
  **Do NOT** keep trimming past the FG% target to make the points number look right.

**Step 6 — tests + close-out.**
- [ ] Unit-test the graduated rate: a `THREE` and a `PERIMETER` **can** now draw a
  foul and an and-1 (they could not before); a `DRIVE` fouls **more often** than a
  `THREE` at equal skill; a **zero multiplier** yields zero for that type (the true
  off-switch, A1 — contrast the §3.11 zero-*base* test, which correctly asserts a
  surviving skill tail).
- [ ] **The FT-count rules, explicitly** — a stopped `THREE` awards **3** FTs
  (the latent bug this pass activates); a stopped `DRIVE`/`POST`/`PERIMETER` awards
  **2**; **an and-1 on a made `THREE` awards exactly 1** (the guard against the
  parallel-graduation wrong turn, C); a §3.10 bonus foul still awards 2.
- [ ] Confirm drive/post foul behavior is **numerically unchanged** from §3.11 — the
  attributability A2 was chosen for. A seed-pinned drive/post foul-rate test is the
  cheapest way to pin it.
- [ ] **Pin the mutual-exclusivity invariant (B):** a single shot attempt can emit
  **at most one** of `SHOOTING_FOUL` / `AND_ONE`, never both. They are mutually
  exclusive by control flow — the pre-shot branch **returns** (`PossessionEngine`
  ~L143), so a stopped shot never reaches the make/miss roll and thus never reaches
  the and-1 roll. Sharing one multiplier table (B) does **not** apply it twice to
  one shot. This is currently guaranteed only by the `return`, so a refactor could
  silently break it — worth a test now that both rolls fire on all four shot types
  and the coincidence is far more likely to occur than it was in §3.11.
- [ ] Confirm `GameSimulatorIntegrationTest` still reconciles: points vs. the event
  log (#020), fouls feeding foul-outs (#023-F) and the §3.10 period tally.
- [ ] **Re-baseline (do NOT delete) the seed-pinned assertions** — this is the
  **largest RNG shift of the arc** (#030, determinism): perimeter/three possessions
  now consume a foul draw, and on a make an and-1 draw, where they previously
  short-circuited. That moves draw counts on the *majority* of possessions, so
  seed-pinned assertions across the **shot** flow shift, not just the foul flow.
- [ ] Update the tests referencing `ShotType.isContactType()` — the third of its
  three call sites. **Confirmed against the code (2026-08), it is exactly two
  files:**
  - `ShotTypeTest.java:33–48` — four assertions (`DRIVE`/`POST` true,
    `PERIMETER`/`THREE` false). **Replace**, don't just delete: assert the new
    `freeThrowsIfFouled()` rule instead (3 for `THREE`, 2 for the other three).
  - `FoulResolverTest.java:391` — a **comment** referencing the `isContactType`
    caller-gate in the zero-base regression test (`aZeroBaseStillLeavesA…`). The
    assertion itself still holds; update the comment to name the new true
    off-switch, a **zero multiplier** (#030 A1).
- [ ] New/changed `sim` classes to ~90%+ line coverage (JaCoCo gate), matching
  §3.7–§3.11; full `mvn clean install` gate green.
- [ ] **Doc close-out (the §3.7–§3.11 pattern — do all of these):**
  - [ ] `game.md` — note that a shooting foul now applies to **all** shot types and
    that a fouled `THREE` awards **3** FTs; the event vocabulary itself is
    **unchanged** (no new outcome strings), which is worth saying explicitly.
  - [ ] `roadmap.md` — flip the §3.12 bullet to `[x]` with an indented italic
    landing summary (final aggregates, the foul/penalty lines, the re-centering
    result, coverage), matching the §3.7–§3.11 shipped bullets.
  - [ ] `decisions.md #030` — add the **implementation note** (`from execution,
    YYYY-MM`): the resolved open-at-execution items (the multiplier constants' final
    shape/names, the `POST` value, the `PERIMETER`/`THREE` numbers, the shot-`BASE_*`
    trim size and final landing), the landing aggregates, coverage, and any
    divergence from A1–E.
  - [ ] `possession-flow.puml` — confirm the two foul gates (the `FoulResolver`
    partition and the `And-1` partition) match what shipped; both were updated in
    this design pass.
  - [ ] `risks.md` — the "calibration drift" entry's **live instance** (points 3.9
    high) is what §3.12 closes; update or remove that paragraph to reflect the new
    landing, and revisit the always-on tolerance-band test now that the intentional
    gap is gone (#030 follow-up + backlog.md).

**Reconciliation invariant:** §3.12 adds **no new event types and no new outcome
strings** — it only changes *how often* existing `FOUL` (`SHOOTING_FOUL`) and
`FREE_THROW` (`MADE_SHOOTING`/`MISSED_SHOOTING`) events fire, and *how many* FTs a
stopped three awards. Points must still reconcile with the event log (#020). Fouls
still feed the per-player `fouls` counter (foul-outs, #023-F) and the §3.10 period
tally. **No new box-score counter, no new `FreeThrowSource`, no re-scored FG.**

**Do NOT (guardrails from #030 / #029 / #028 / #020):**
- Do **not** keep `isContactType()` "just as a helper" — it is deleted (A1). The
  rate table is the single source of truth for how often a type draws contact.
- Do **not** re-derive `BASE_NO_BASKET_FOUL` as a new league-wide base — it stays `0.15`
  meaning the **drive** rate, with the table anchored on it (A2). Re-deriving it
  reopens a §3.4-calibrated number in the same pass that adds two scoring sources,
  making a re-solve error indistinguishable from the new lift (the #029 A1 trap).
- Do **not** make `AND_ONE_FREE_THROWS` graduate by shot type (C) — a made three +
  foul is **1** FT, not 3.
- Do **not** trim `BASE_NO_BASKET_FOUL` to remove points (E) — wrong-way lever (#028).
- Do **not** move `BONUS_FOULS_PER_PERIOD` (D) — it is the real NBA rule.
- Do **not** add a `FreeThrowSource` for the 3-FT trip (C) — no consumer asks
  (#014/#017/#020).
- Do **not** touch flagrant/technical fouls — that is **§3.13**, its own design pass.

---

## Verified facts (the `sim` package map — confirmed against the code 2026-08, post-§3.11)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The binary gate §3.12 DELETES (#030 A1) — its exactly three call sites:**
- `ShotType.isContactType()` = `DRIVE || POST`. Read in exactly three places, all
  handled by the plan above: `FoulResolver.isFoul:31` (returns `false` immediately
  for non-contact types → becomes a multiply, Step 3), `PossessionEngine:213` (the
  and-1 gate in the `if (made)` block, #029 A2 → deleted, Step 4), and tests
  (Step 6).
- `SimConfig.BASE_FOUL = 0.15` (**renamed `BASE_NO_BASKET_FOUL` by #030 F**) — the pre-shot foul rate, meaning "P(contact stopped
  the shot)" **on drive/post**. §3.4-calibrated; §3.11 deliberately left it alone,
  and **#030 A2 keeps it untouched** as the `DRIVE = 1.0` anchor for the new
  multiplier table. It is also a **wrong-way** points lever (#028) — never trim it
  to remove points.
- `SimConfig.AND_ONE_BASE = 0.055` + `AND_ONE_SENSITIVITY = 0.10` — the §3.11 and-1
  rate, landing 1.67 and-1s/team/game (6.4% of made contact FG).

**The FT count/source seam — ALREADY BUILT (§3.11, #029 B/D):**
- `PossessionEngine.awardFreeThrows(data, shooter, shootingTeamId, offTeamId,
  defTeamId, period, sequence, count, source, rng)` takes a **per-situation count**
  and a **`FreeThrowSource`**. Three call sites today: shooting foul
  (`FREE_THROWS_PER_FOUL` = 2, `SHOOTING`), §3.10 bonus (2, `BONUS`), and-1
  (`AND_ONE_FREE_THROWS` = 1, `AND_ONE`). **§3.12's fouled-three passes `3` here** —
  this is the seam, no new machinery needed.
- `FreeThrowSource` enum owns its `outcome(boolean made)` string —
  `MADE_SHOOTING` / `MISSED_BONUS` / `MADE_AND_ONE`. The `MADE`/`MISSED` prefix
  **leads** so `startsWith("MADE")` reads keep working; a new source (if §3.12 or
  §3.13 needs one) is a new enum constant, no schema change (#020).

**The rare-event probability machine (§3.7 + §3.10 + §3.11):**
- `SimConfig.rareEventProbability(base, drivingSkill, opposingSkill, sensitivity)`
  clamps to **[0, PROB_CEILING]** with **no floor** — a thin base stays tunable
  downward. `clampProbability` (floor `PROB_FLOOR = 0.02`) is for normal-frequency
  outcomes only. **Each rare event carries its OWN sensitivity**:
  `BLOCK_SENSITIVITY = 0.12`, `REBOUND_FOUL_SENSITIVITY = 0.10`,
  `AND_ONE_SENSITIVITY = 0.10`. The global `SENSITIVITY = 0.5` swamps a thin base —
  three phases have now hit that; don't hit it a fourth.
- **A zero base does NOT switch a rare event off** (measured in §3.11): the formula
  is `base + sensitivity × (driving − opposing)/10`, so the skill term alone keeps
  the rate positive whenever the driving side is favored. Only the **caller's gate**
  disables a feature. Relevant to §3.12 if any shot type is meant to be foul-free.
- `FoulResolver.isFoul` (pre-shot) and `FoulResolver.isAndOne` (post-make) share the
  same avg-10 wiring: shooter `foulDrawing × fatigueFactor` vs. defender
  `(SCALE_AVG*2 − foulProne) × fatigueFactor`, scaled by `defensivePressure`.

**The team-foul / bonus substrate (§3.10, #028) — reused unchanged by §3.11:**
- `GameData.isInBonus(teamId, period)` + `periodFoulCount` derive the penalty from
  the `FOUL` event log — no stored counter, emit-then-count.
- `game_event.committing_team_id` carries the committer on every `FOUL`.
  One-sided fouls (`SHOOTING_FOUL`, `AND_ONE`) pass `defTeamId`; the two-sided
  `REBOUNDING_FOUL_*` passes whichever side the roll picked.
- `SimConfig.BONUS_FOULS_PER_PERIOD = 5`, `FREE_THROWS_PER_FOUL = 2`.

**`test/.../sim/CalibrationHarness.java`:** disabled-by-default
(`-Dcalibration=true`), and takes **`-DcalibrationSeed=NNNN`** (§3.11) so the
multiple-run discipline is runnable. Prints the §3.4 aggregates + §3.5
minutes/period-FG% + §3.7 blocks + §3.8 OOB + §3.9 turnover-cause mix + §3.10
team-fouls/bonus + §3.11 and-1 rate + FT-source split. The FT split is read off the
self-describing outcome, with an `UNKNOWN` bucket that surfaces an untagged FT.
Re-run after any `SimConfig` change.
**⚠ One line is §3.12-fragile** (fix it in Step 5 *before* reading any number):
`madeContactShots` (`:223–227`) is hardcoded `startsWith("MADE") &&
(endsWith("DRIVE") || endsWith("POST"))`, and `:412–417` prints and-1s as a share
of it. §3.12 widens the **numerator** to all four shot types but not that
**denominator**, so the percentage silently inflates. Widen it to all made FG and
re-label; compare across the change on **and-1s/team/game** (1.67), which is
denominator-independent.

**§3.11 landing to recalibrate against (harness, ~102 games × 5 seeds, to the mean):**
`115.9 pts / 46.8% FG / 37.6% 3P / 27.5 ast / 13.9 TO / 4.9 blk / 2.8 OOB`,
and-1s 1.67/team/game (6.4% of made contact FG), FT split SHOOTING 90.6% /
AND_ONE 5.8% / BONUS 3.6%, fouls 4.41/team/period, 43.4% of team-periods in the
penalty. §3.4 targets remain ~112/47/36/26/14 — **points are knowingly 3.9 high**,
deferred to §3.12 (see roadmap + #029's follow-up).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md). *(The **`decisions.md` condense pass** is now
  **UNBLOCKED** — §3.11 shipped, so §3.7–§3.11 is a complete arc. Preserve the four
  findings §3.11 actually reached for; see the backlog entry.)*
  *(Also parked there: **loading the `SimConfig` constants from flat properties
  files as swappable profiles**, so the harness can be run against several configs
  and compared (`-DcalibrationProfile` beside the existing `-DcalibrationSeed`) —
  user direction 2026-08, deliberately scheduled for **after §3.13**, since changing
  how constants load mid-arc would forfeit exact reproducibility of a prior landing.
  **§3.12 keeps editing `SimConfig` directly** and runs its multiplier sweep by hand,
  the way §3.11 did — that hand-run sweep is precisely the evidence the profile chore
  has a real consumer, so note how painful it was.)*
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md). *(Includes the parked cap 3→5 tuning idea — do NOT touch the
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` value without its own recalibration pass.)*
- **§3.13 (flagrant/technical fouls)** → roadmap.md's Possession-fidelity section, a
  numbered sub-phase needing its own design pass (decisions.md #029 follow-up).
  **Not this pass** — it changes *who shoots*, adds a possession-retention path no
  current model has, and adds ejections.
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play pagination
  + a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **§3.7 seams left open by #025** (carry forward): a skilled-blocker recovery edge
  (Decision D — additive if a consumer ever wants it); shot-clock pressure on
  block-recovered second-chance possessions (Decision E → §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027** (carry forward): §3.7-E shot-clock pressure (still
  parked); finer turnover sub-types (`DOUBLE_DRIBBLE`/`CARRYING`/`PALMING`/etc. — add a
  weight + enum value when a consumer wants the granularity).
- **§3.10/§3.11 seams left open by #028/#029** (carry forward):
  `committing_team_id` is **populated and queryable but not surfaced on the OpenAPI
  `GameEvent`** (the and-1 `FOUL` populates it too) — additive whenever a
  play-by-play or Phase-4 stats consumer wants it; the observed off/def
  rebounding-foul split (**78/22**) drifts from the configured 75/25 because
  defensive fouls compound through retained possessions — back-solve only if a
  consumer needs the observed split to hit a target.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources; it
  reports the §3.4 aggregates + the §3.5 minutes/period-FG% distributions + the §3.7
  blocks line + the §3.8 OOB line + the §3.9 turnover-cause mix + the §3.10
  team-fouls/bonus lines + the §3.11 and-1 rate + FT-source split. Re-run it after
  any `SimConfig` change.
