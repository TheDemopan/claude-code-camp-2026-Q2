## Technical Goal
Give the Java Boukensha agent persistent spatial memory and goal-keeping across sessions, by converting a prose memory schema — written for a CLAUDE.md-driven agent and enforced through instructions — into a schema enforced by code. Secondary goal: add the capability to a codebase whose existing 13 launchers are graded submission artifacts, without introducing regression risk to any of them.

## Technical Uncertainty
The schema in `docs/plans/java_port/memory_implementation.md` relies on the model choosing to maintain four files correctly. `0_preweek.md` records that approach failing on both gemma4:26b and haiku 4.5 (forgotten parameters, forgotten file updates, eventually hallucinated rooms). Does moving enforcement from instructions into code eliminate that failure class, or merely relocate it?

Is CircleMUD room output regular enough to auto-capture topology without model involvement, given dark rooms, combat interleaving, ANSI colour codes, and asynchronous server chatter?

Can a substantial feature be added to a graded codebase with genuinely zero regression risk, or is some shared-code modification unavoidable?

Does supplying a routing tool substitute for spatial reasoning — the capability every prior approach was weakest at?

## Technical Hypotheses
Capturing topology automatically from tool output removes the discipline requirement entirely, because the model is never asked to record anything and therefore cannot forget.

CircleMUD output is regular enough to parse reliably, and where it is not, refusing to parse is safe: a missing map entry is recoverable, a wrong one is not.

A purely additive architecture — new files only — bounds regression risk to compilation, which is cheap to verify.

An agent given breadth-first route-finding over its own accumulated map does not need to reason spatially at all.

## Technical Observations

    Approach 1: Schema translation — prose rules into code invariants
Model: Opus 5

### What worked:

The four-file schema was kept **verbatim**: `map.csv` (topology), `rooms.jsonl` (observations), `state.json` (working state), `notes.md` (durable knowledge). Only the enforcement mechanism changed.

Three prose rules became code invariants rather than hopes:
- *"Do not assume every exit has a reverse exit"* → `setExit()` writes only the direction actually traversed.
- *"Reuse an existing room ID when there is sufficient evidence"* → identity matched on room name + observed exit set, never on description wording, which varies with lighting and mob presence.
- *"Coordinates must never override observed exits"* → BFS traverses the exit graph only; coordinate fields are carried but unused in routing.

### What didn't work & Unexpected behavior:

The first design draft modified `Boukensha.java`, `MudTools.java`, and `Step08TheReplLoop.java` — six of the thirteen graded steps sharing modified code — and injected a memory brief into the system prompt used by five of them. Since those steps' outputs *are* the graded artifact, this would have altered exactly what was being assessed. **This was caught only because the user asked whether the implementation would harm the numbered files.** The design was reversed before any code was written.

    Approach 2: Parser validated against real captured data
Model: Opus 5

### What worked:

Rather than designing the parser against assumed format, it was designed against real output extracted from existing session logs. The format proved highly regular: room name on the first line under ANSI colour `0;33`, description indented three spaces, `[ Exits: ... ]` under colour `0;36` as a stable anchor, and a trailing `23H 100M 84V >` prompt that yields hit points, mana, and movement free on every single command.

Replayed across every `look`/`move` result ever logged: **99 room observations parsed, 10 correctly rejected, 33 distinct rooms recovered** — including the Bakery, Temple Square, and the full sewer network.

The load-bearing design decision was returning an empty result rather than guessing. Score output, dark rooms, bare prompts, and command echoes all reject cleanly.

### What didn't work & Unexpected behavior:

A stray dead statement left in `MemoryStore` failed compilation — trivial, but a reminder that compiling after each unit is non-optional rather than a formality.

    Approach 3: Additive architecture under a grading constraint
Model: Opus 5

### What worked:

Auto-capture was implemented as a **decorator** rather than a hook. `Registry.tool()` overwrites by name and `Tool.getBlock()` exposes the original block, so `look` and `move` could be re-registered wrapping the originals — calling through, folding the response into the store, returning it unchanged. The model sees identical tools; the MUD sees identical traffic; **no existing source file was modified.**

Regression risk collapsed to compilation, verified by a new free `bin/verify` (stub smoke test plus the four offline steps).

### What didn't work & Unexpected behavior:

Investigating the regression question surfaced a **pre-existing defect introduced in the previous session**: an earlier fix had made `Boukensha.run` auto-register MUD tools, while steps 10 and 12 still registered them manually — opening two telnet sessions and two logins per run, with the first silently orphaned. It went unnoticed because only one of four affected callers had been retested. Fixed with an idempotency guard and re-verified live before memory work began.

    Approach 4: Live behaviour
Model: Opus 5 (harness), claude-haiku-4-5 (agent under test)

### What worked:

Three moments each validated a specific design rule under real conditions:

**A blocked move wrote nothing.** `"Alas, you cannot go that way..."` produced no phantom room and no phantom edge — the write-nothing-rather-than-junk rule on its first genuine encounter.

**Route-finding refused to fabricate.** Asked to return to a previously visited room, `find_route` answered *"No known route… the connecting rooms have not been explored yet."* The map held `1→s→2→s→3`, but the reverse edges were still unconfirmed, so BFS declined rather than assuming symmetry. The agent then walked north anyway, which taught the map those edges; on the next run the same call returned `"north, north"` and was followed exactly.

**Cross-session persistence.** A fresh process recalled all mapped rooms via `map_summary` without a single MUD call to rediscover them.

### What didn't work & Unexpected behavior:

The initial `find_route` refusal read as a failure and was momentarily mistaken for one. It was correct behaviour — the no-reverse-exit rule, which looked pedantic when transcribed from the schema document, was the thing preventing a fabricated route.

The model again passed empty strings rather than omitting optional arguments; normalisation ported earlier from the Ruby primitives absorbed it silently.

## Technical Conclusions

**Where a schema is enforced matters more than how well it is specified.** The schema document was unchanged between the failing CLAUDE.md approach and the working Java one. What changed was that the model is no longer asked to maintain it. Bookkeeping discipline is the wrong thing to request from a model; it is the right thing to remove from its responsibilities entirely.

**"Refuse rather than guess" is the correct default for agent memory.** Every rejection observed — the blocked move, the score output, the unroutable destination — was a case where guessing would have produced plausible, wrong, durable data. A missing map entry costs one exploratory move; a fabricated edge silently corrupts every future route through it.

**Constraints improved the architecture rather than compromising it.** The requirement not to disturb graded files forced a decorator design that is strictly better than the original hook: no coupling to `MudTools`, memory removable by deleting files, and regression risk reduced to compilation. The stronger constraint produced the cleaner design.

**A tool can substitute for a model capability.** Spatial reasoning was the weakest capability across every approach in `0_preweek.md`. Rather than prompting the model to reason better about space, breadth-first search over its own accumulated map removes the requirement — the model asks for directions and follows them. Capability gaps in a model can sometimes be closed by moving the work into the harness instead.

**Regression questions are worth asking before, not after.** Both defects found in this phase — the six-graded-files design and the double telnet session — surfaced from asking "what does this change touch?" rather than from testing. Neither would have been caught by the passing tests that existed at the time.

**Final state:** 8 new source files (`memory/` package, `MemoryTools`, `Step13Memory`, `ParserTest`), 2 new launchers (`bin/13_memory`, `bin/verify`), zero modifications to graded step files. Parser validated against 99 real room observations; persistence, automatic mapping, and route-following each verified live against a running tbaMUD server.
