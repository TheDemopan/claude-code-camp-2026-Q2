# Boukensha — Java Port

A Java port of the Boukensha LLM agent framework. Runs autonomous agents that call tools,
talk to multiple LLM backends, and play a CircleMUD/tbaMUD world.

This is a 1:1 port of `week1_baseline/ruby/`. Where the two differ, the Ruby original is
the source of truth and the difference is documented under
[Differences from the Ruby original](#differences-from-the-ruby-original).

---

## Quickstart

### 1. Prerequisites

- **A JDK 17+** — a JRE is not enough, `bin/boukensha` needs `javac`. It looks in
  `$JAVA_HOME`, then `PATH`, then JDKs bundled with VS Code / IntelliJ / SDKMAN.
- **Dependencies in `~/.m2`** — Jackson, OkHttp, dotenv-java, SnakeYAML. If missing:
  `mvn dependency:go-offline`.
- **An Anthropic API key** (or OpenAI / Gemini / Ollama).
- **A MUD on `localhost:4000`** — only for steps 10–12.

### 2. Configuration

Config lives in a `.boukensha` directory, resolved as `$BOUKENSHA_DIR` → `~/.boukensha`.
`bin/boukensha` defaults it to the **repo-root `.boukensha`**, shared with the Ruby port.

```
.boukensha/
├── .env               # ANTHROPIC_API_KEY=sk-ant-...
├── settings.yaml
└── prompts/
    ├── system.md            # default system prompt
    └── player/system.md     # used when prompt_override.system is true
```

```yaml
# settings.yaml
tasks:
  player:
    provider: anthropic          # anthropic | openai | gemini | ollama | ollama_cloud
    model: claude-haiku-4-5
    prompt_override:
      system: true               # prefer prompts/player/system.md

mud:
  host: localhost
  port: 4000
  username: dummy
  password: secret

agent:                           # optional; defaults shown
  max_iterations: 25
  max_output_tokens: 1024
  max_turn_tokens: 60000
  compaction_threshold: 0.85
```

### 3. Run

```bash
cd week1_baseline/java
./bin/00_config          # verify config loads — no API call, costs nothing
./bin/07_the_run_dsl     # a real agent turn
./bin/11_tui             # interactive, with a live view of the agent working
```

`bin/boukensha` compiles on first run and only recompiles when sources change.

---

## The step launchers

One per teaching step, mirroring `ruby/bin/`. Steps are cumulative — each adds one concept.

| Launcher | Adds | Calls the API? |
|---|---|---|
| `bin/00_config` | `Config`, `Tasks` — settings.yaml, .env, prompt resolution | no |
| `bin/01_struct_skeleton` | `Context`, `Message`, `Tool` | no |
| `bin/02_the_registry` | `Registry` — registration + dispatch | no |
| `bin/03_prompt_builder` | `PromptBuilder`, backends — prints the wire payload | no |
| `bin/04_api_client` | `Client` — one real request | yes |
| `bin/05_agent_loop` | `Agent` — the iterate/tool-call loop | yes |
| `bin/06_the_logger` | `SessionLogger` — JSONL session logs | yes |
| `bin/07_the_run_dsl` | `Boukensha.run` — everything wired for you | yes |
| `bin/08_the_repl_loop` | `Repl` — multi-turn, shared context | yes |
| `bin/09_global_executable` | the same REPL via the project launcher | yes |
| `bin/10_standard_tool_library` | `MudTools` — 27 MUD tools | yes + MUD |
| `bin/11_tui` | `Tui` — live event view | yes + MUD |
| `bin/12_context` | token ceilings + compaction | yes + MUD |

Run any main class directly: `./bin/boukensha com.boukensha.examples.SmokeTest`.
`./bin/boukensha --help` prints usage.

---

## Using the library

### One-shot run

```java
String result = Boukensha.run(
    "Read README.md and summarise it.",
    dsl -> dsl.tool("read_file", "Read a file from disk",
        Map.of("path", Map.of("type", "string", "description", "Path to read")),
        args -> Files.readString(Path.of(String.valueOf(args.get("path"))))));
```

`Boukensha.run` reads config, builds the backend, wires the client, logger, and agent, runs
one turn, and closes the log. Override anything via `Boukensha.Options`.

Ruby uses `instance_eval` so the block sees `tool` as a bare method. Java has no equivalent,
so **the block receives the `RunDSL` as a parameter**. Use `dsl.registry()` to hand the whole
registry to a tool library.

### Assembling it yourself

```java
Config config = new Config();
Context context = new Context(systemPrompt, Models.contextWindow(model), null, 0.85);
Registry registry = new Registry(context);
FileSystemTools.register(registry, workingDir);

Backend backend = new AnthropicBackend(config.env("ANTHROPIC_API_KEY"), model);
PromptBuilder builder = new PromptBuilder(context, backend);
Client client = new Client(builder);
SessionLogger logger = new SessionLogger(null, config.getDir() + "/sessions", null, Map.of());

Agent agent = new Agent(context, registry, builder, client, logger, 25, 60_000, 1024);
context.addMessage("user", "Where am I?");
String reply = agent.run();
logger.close();
```

### Tool libraries

```java
FileSystemTools.register(registry, workingDir);              // pwd, read_file, write_file, delete_file
ShellTools.register(registry, workingDir, 30, List.of("git")); // run_command (timeout + allowlist)
MudTools.register(registry, host, port, username, password);   // 27 MUD tools, auto-connects
```

`FileSystemTools` sandboxes every path to the working dir and returns an error string rather
than throwing when one escapes. `ShellTools` takes an optional executable allowlist — omit it
and any command runs, so pass one when the agent's input isn't trusted.

---

## Architecture

```
Boukensha ──── run()/config()          facade
   │
   ├── Config ─── Tasks/PlayerTask     settings.yaml, .env, prompts
   ├── Context ── Message, Tool        state: history, tools, token accounting
   ├── Registry                        tool registration + dispatch
   ├── PromptBuilder ── Backend        per-provider wire format
   │                     ├── AnthropicBackend
   │                     ├── OpenAIBackend
   │                     ├── GeminiBackend
   │                     ├── OllamaBackend
   │                     └── OllamaCloudBackend
   ├── Client                          HTTP + retry
   ├── Agent                           the loop
   ├── SessionLogger                   JSONL + subscribe()
   ├── Repl ───── Tui                  interactive
   └── mud/ ───── MudSession, MudPrimitives
```

**`Agent`** iterates until the model stops requesting tools. Two independent ceilings —
`max_iterations` and `max_turn_tokens` — are *trigger thresholds*, not hard caps: on reaching
either, the agent stops starting work and makes one final tools-disabled wind-down call so the
turn ends in character. A tool that throws is caught and returned to the model as
`ERROR: ...` rather than killing the turn.

**`Client`** retries 3 times with exponential backoff (0.5s → 1s → 2s) on `IOException` and on
408/409/429/500/502/503/504. The request is built **once, before** the loop, so a payload
serialization bug fails immediately instead of being retried.

**`Context`** tracks two separate token figures: `currentTokens` (window pressure, from the
last response's `input_tokens`) and `turnTokens` (cumulative spend this turn). Compaction drops
the oldest 40% of messages, keeping at least 2.

**`MudSession`** holds a telnet connection with a background reader thread, strips telnet IAC
negotiation, and reads until CircleMUD's `"> "` prompt sentinel.

---

## Session logs

Every run appends JSONL to `<config-dir>/sessions/<session-id>.jsonl`. Each line carries
`session_id`, an ISO-8601 `at`, a `phase`, and phase-specific fields.

```
session_start → turn → iteration → prompt → plan → response(tool_use)
              → tool_call → tool_result → iteration → prompt
              → response(end_turn) → turn_end
```

Other phases: `limit_reached`, `compaction`, `reasoning`, and `raw` (debug only, via
`Boukensha.debug()`). `SessionLogger.subscribe(...)` streams every event to a listener — that
is how the TUI renders live.

```bash
# what tools did this run call?
grep -o '"phase":"tool_call","name":"[a-z_]*"' .boukensha/sessions/<id>.jsonl
```

---

## Verification status

**Verified live** against the real Anthropic API and a real tbaMUD server:

| Area | Evidence |
|---|---|
| Config, `.env`, prompt override | `00_config` reads real settings |
| Payload construction | `03_prompt_builder` emits valid Anthropic JSON |
| Single request | `04_api_client` — real response |
| Agent loop + tool dispatch | `05`/`07` — real `read_file` calls, verified in logs |
| Logger phases | `06` — all phases present |
| REPL multi-turn history | `08` — `message_count` grew 1 → 3 → 5 across turns |
| MUD telnet, login, IAC | probe returned live room + score |
| 27 MUD tools | `10` — 5 real MUD calls across 3 iterations |
| TUI live stream | `11` — events rendered as they happened |
| Agent loop in isolation | `SmokeTest` — 17 assertions, no network |

**Not verified:**

- **OpenAI, Gemini, Ollama, OllamaCloud** — they compile and their transforms were ported from
  the Ruby source, but **no request has ever been sent** through any of them.
- **The wind-down path** — no run has reached `max_iterations` or `max_turn_tokens`, so
  `wrapUp` and its fallback message have never executed.
- **Steps 06/08 error branches** — `ApiError` handling in the REPL is untested.

### Running the smoke test

```bash
./bin/boukensha com.boukensha.examples.SmokeTest
```

Spins up a local stub HTTP server speaking the Anthropic response shape and drives a full
two-iteration turn with a tool call. No credentials, no network egress, no cost — the fastest
way to check the loop after a change.

---

## Differences from the Ruby original

| Area | Ruby | Java |
|---|---|---|
| `run` block | `instance_eval`, bare `tool` | block receives `RunDSL`; `dsl.tool(...)` |
| `.env` | `Dotenv.load` mutates `ENV` | Java can't mutate the process env — use `config.env(name)` |
| **TUI** | charm (bubbletea/lipgloss/bubbles) | **reimplemented**, not ported — plain ANSI event stream; no viewport or key handling |
| `mud_manager` | external gem in `week0_explore/` | ported into `com.boukensha.mud` |
| `Tasks` + step-12 `Config` | step 12 deletes `tasks/` | both APIs kept so every step maps 1:1 |
| Tool block | Ruby block, keyword args | `Function<Map<String,Object>, Object>` |
| `Primitives` | returns a `Command` struct | returns the raw command `String` |

Two Ruby-side quirks worth knowing: `ruby/bin/01`–`12` point `BOUKENSHA_DIR` at
`week1_baseline/.boukensha`, which does not exist — only `00_config` uses the correct
repo-root path, and the Java launcher follows `00_config`. And `FileSystemTools` omits
`list_directory` / `search_files` because they are commented out in the Ruby source.

---

## Project layout

```
week1_baseline/java/
├── bin/                    boukensha + 13 step launchers
├── prompts/system.md       default system prompt
├── pom.xml
└── src/main/java/com/boukensha/
    ├── Agent, Boukensha, Models, Version
    ├── api/       Client, backend/{Anthropic,OpenAI,Gemini,Ollama,OllamaCloud}
    ├── config/    Config
    ├── dsl/       RunDSL
    ├── examples/  Step00..Step12, Example, SmokeTest
    ├── exception/ BoukenshaException, ApiError, UnknownToolError, UnsupportedModelError
    ├── logger/    SessionLogger
    ├── model/     Context, Message, PromptBuilder
    ├── mud/       MudSession, MudPrimitives
    ├── repl/      Repl
    ├── tasks/     Task, PlayerTask
    ├── tool/      Tool, Registry
    ├── tools/     FileSystemTools, ShellTools, MudTools
    └── tui/       Tui
```

## Building

`bin/boukensha` handles compilation. Maven also works if installed:

```bash
mvn compile
```

Java 17+. Dependencies: Jackson 2.17, OkHttp 4.11, dotenv-java 3.0, SnakeYAML 2.0, JUnit 5.
