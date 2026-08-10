## Technical Goal
Port the Boukensha LLM agent framework from Ruby to Java at 1:1 behavioral parity, spanning all 13 cumulative teaching steps (config → struct skeleton → registry → prompt builder → API client → agent loop → logger → run DSL → REPL → global executable → standard tool library → TUI → context management), and establish what evidence is required before a cross-language port can be called complete.

## Technical Uncertainty
Can an LLM coding agent port a multi-file, ~20k-line dynamically-typed framework to a statically-typed target while preserving runtime behavior, not merely structure?

Does successful compilation in a statically-typed language correlate with behavioral correctness, or does it produce false confidence?

Which Ruby idioms (`instance_eval` DSLs, `Dotenv` mutating `ENV`, `Struct`, duck typing, keyword-argument blocks) survive translation, and which require an explicit redesign rather than a mechanical port?

Can protocol-level code (telnet/IAC negotiation, CircleMUD login sequencing) be ported faithfully without access to a live server during development?

## Technical Hypotheses
Static typing will catch the majority of port defects at compile time, making compilation a reasonable proxy for correctness in a language-to-language port.

Ruby's dynamic idioms will require redesign at a small number of identifiable points, and those points can be predicted from reading the source before porting.

Stub-based verification (a local HTTP server speaking the provider's response shape) will validate the agent loop without credentials, cost, or network egress.

Scope can be estimated accurately from the file sizes of the modules to be ported.

## Technical Observations

    Approach 1: Compile-driven porting
Model: Haiku 4.5 (early session), Opus 5 (majority of the port)

### What worked:

Structural translation was largely mechanical. Class-per-file layout, package structure, and the backend strategy pattern mapped cleanly from Ruby modules.

The compiler caught genuine signature and type errors immediately — wrong `Agent` constructor arity, missing imports, incompatible collection types.

### What didn't work & Unexpected behavior:

**Compilation proved a weak correctness signal.** Four defects passed `javac` cleanly while being functionally wrong:

- `Config.loadEnv()` called `Dotenv.load()` and **discarded the returned object**. Unlike Ruby's `Dotenv.load`, which mutates `ENV`, Java cannot mutate the process environment — so `System.getenv()` never saw the key and the entire `.env` load was a silent no-op.
- `SessionLogger` was fully implemented but **never wired into `Agent`** — dead code. Step 6 was reported complete while the logger had zero call sites.
- `Agent.extractText` joined content blocks with `""` where Ruby joins with `"\n"`.
- `Context.compactMessages` scaled `currentTokens` proportionally instead of zeroing it.
- Tool dispatch was unguarded, so a throwing tool would kill the turn; Ruby converts it to an `ERROR: ...` tool result fed back to the model.

**Edits made without recompiling introduced new defects.** A "fix" round produced two variable-shadowing errors (`String content` colliding with a parameter of the same name in `assistantMessage`) that were reported as complete and were not. Work was declared finished twice on the strength of pattern-matching rather than compiler output.

**No JDK was on `PATH`** — only a JRE. `javac` was eventually located inside a VS Code extension's bundled runtime, which the project launcher now discovers at runtime.

    Approach 2: Stub-based verification
Model: Opus 5

### What worked:

A local `com.sun.net.httpserver` stub returning canned Anthropic-shaped responses drove a complete two-iteration turn with a real tool call — no credentials, no egress, no cost. 17 assertions covering iteration count, tool invocation, `tool_use_id` round-trip, message ordering, token accounting, and every JSONL phase.

This validated the agent loop, registry dispatch, the `tool_result` round-trip, and logging in isolation, and remained the fastest regression check for the rest of the port.

### What didn't work & Unexpected behavior:

The stub was written *after* the loop was declared finished, not before. It should have been the first artifact built, since it is the only thing that can validate the loop without external dependencies.

A stub cannot validate what it encodes: it asserts the author's assumptions about the wire format, not the provider's actual behavior. It caught zero of the defects that live execution later surfaced.

    Approach 3: Live execution
Model: Opus 5 (harness), claude-haiku-4-5 (agent under test)

### What worked:

Live runs against the real Anthropic API and a live tbaMUD server validated every layer end to end: config loading, payload construction, auth headers, HTTP with retry, the agent loop, tool dispatch, token accounting, and JSONL output.

Session logs provided objective evidence rather than self-report — confirming, for example, that `read_file{path: README.md}` genuinely fired rather than the model answering from context.

REPL multi-turn history was verified by `message_count` growing 1 → 3 → 5 across turns, with turn 2 correctly answering a question about turn 1.

The ported telnet layer connected to the real MUD, completed the CircleMUD login dance, stripped IAC negotiation cleanly, and returned character stats matching the persisted memory file exactly (Dummy the Swordpupil, 23H/100M/85V, AC 39/10, 1930 exp).

A full agent turn drove the MUD: `mud_connect → look → check{score} → check{exits} → look`, 3 iterations, 11,342 tokens, 27 tools registered.

### What didn't work & Unexpected behavior:

**The first live attempt failed on billing**, not code — `credit balance is too low`. Diagnostically useful: the API *authenticated* the key before declining, which proved the key, the `.env` quoting, and the auth header were all correct. Anthropic Console API credits are a separate billing system from a Claude subscription, which is a common and easily-missed distinction.

**Two defects were discoverable only by running.** The `.env` no-op above, and the launcher defaulting `BOUKENSHA_DIR` to `week1_baseline/.boukensha` — a directory that does not exist.

**The Ruby source is internally inconsistent.** `ruby/bin/01`–`12` point `BOUKENSHA_DIR` at that same non-existent path; only `00_config` uses the correct repo-root path. The Java port initially inherited the broken convention by copying the majority case.

**The model ignored an explicit tool-description warning.** `look`'s description says "do NOT pass target: 'room'"; the model's first call passed exactly that, then retried with empty strings. The empty-string normalization ported from Ruby's `Primitives.look` absorbed it correctly — a line that looked like defensive boilerplate during the port and earned its place on the first real run.

    Approach 4: Hidden dependencies and untranslatable components
Model: Opus 5

### What worked:

`mud_manager` — an external gem living in `week0_explore/`, outside the port's stated scope — was ported into `com.boukensha.mud`: `MudSession` (268 lines: telnet, background reader thread, IAC stripping, prompt-sentinel reads, login sequencing) and `MudPrimitives` (418 lines reduced to the 23 command builders actually referenced).

### What didn't work & Unexpected behavior:

**Scope was underestimated by ~2.4x at the point of commitment.** The MUD tier was quoted as ~830 lines based on `mud.rb` (480) and `tui.rb` (349). Line 1 of `mud.rb` is `require "mud_manager"`, adding 691 unaccounted lines. The estimate was produced without reading the file's imports.

**One component could not be ported at all.** `tui.rb` is built on `charm` — Ruby bindings for bubbletea/lipgloss/bubbles, an Elm-architecture TUI with viewport and progress widgets. Java has no equivalent library, so reproducing it would mean hand-writing a terminal framework. It was **reimplemented** — the same information (iterations, tool calls, results, context pressure, token spend) rendered in plain ANSI over the logger's `subscribe()` hook — and labelled as a reimplementation in both the source and the README. The four-zone layout and key handling are absent.

**Two API surfaces had to coexist.** Ruby step 12 deletes `tasks/` and folds those lookups onto `Config`. Steps 00–11 depend on `Tasks::Player`. A single consolidated Java library therefore carries both surfaces so every step maps 1:1 — a divergence from any single Ruby snapshot, adopted to preserve the teaching progression.

## Technical Conclusions

**Compilation is necessary but far from sufficient.** Every defect that mattered in this port — a no-op `.env` loader, an unwired logger, a wrong join separator, a token-scaling bug, an unguarded dispatch — passed the compiler. The hypothesis that static typing would catch the majority of port defects was wrong: it caught signature errors and no semantic ones. Compiler output measures whether code is *well-formed*, not whether it is *the same program*.

**Verification is tiered, and each tier catches a disjoint defect class.** Compilation catches shape. Stubs catch control flow and internal contracts (loop structure, dispatch, logging) with no credentials or cost. Live execution catches integration and environment (credentials, path resolution, protocol handshakes, provider quirks). Skipping a tier does not defer its defects — it hides them until the most expensive tier.

**Dynamic-to-static porting concentrates its difficulty at idiom boundaries, and those boundaries are predictable only by reading, not by scanning.** `instance_eval` → explicit parameter, `Dotenv` mutating `ENV` → an accessor method, `Struct` → POJO, keyword blocks → `Function<Map, Object>`. Each was a small localized redesign. The failure mode was not translating them badly; it was not noticing one (`Dotenv`) until runtime.

**Cross-language ports surface hidden dependencies late, and scope estimates made without reading imports are unreliable.** A single `require` at the top of one file expanded the remaining work by 691 lines after the commitment to "port everything" had been made.

**Some components cannot be ported, only reimplemented — and the distinction must be declared rather than absorbed.** Where the source language's ecosystem has no target equivalent (charm/bubbletea), the honest output is a labelled reimplementation delivering the same purpose, not a silent approximation presented as a port.

**Agent self-report is not evidence; artifacts are.** Session logs, compiler exit codes, and assertion counts repeatedly contradicted "this is complete." The port's verification status is documented as a table separating what has live evidence from what does not — four of the five LLM backends have never sent a request, and the wind-down path that fires on hitting an iteration or token ceiling has never executed.

**Final state:** 47 source files, 55 classes, 13 step launchers, 5 backends, 27 MUD tools, 1 stub-driven smoke test. Steps 00–12 execute; steps 00–12 verified live against the real Anthropic API, with 10–12 additionally verified against a live tbaMUD server.
