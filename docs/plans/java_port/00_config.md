# Boukensha Ruby → Java Port Plan

## Executive Summary

**Project:** Boukensha - An LLM agent framework (256 Ruby files, ~19.8K LOC)  
**Goal:** Achieve performance, type safety, and ecosystem benefits via Java port  
**Strategy:** Strict 1:1 rewrite to preserve behavior; redesign only where Java idioms provide clear wins  
**Timeline:** ASAP (48-72 hours) - Full port with parallel workstreams  
**Team:** 3-4 devs working in parallel; staggered dependencies  

---

## Phase Breakdown — Parallel Workstreams (48-72 Hours)

**Three Independent Work Tracks — Start ALL immediately, integrate hourly:**

---

### TRACK A: Core Agent Loop (Dev 1) — 8-12 hours
**Blocker:** None. Start immediately.

| Task | File | Status |
|---|---|---|
| Maven project + package structure | `pom.xml` | 1 hour |
| `Agent.java` (iteration, tool invoke, limits) | Core logic | 3 hours |
| `Tool.java`, `Registry.java` | Tool management | 2 hours |
| Integration test setup | JUnit framework | 1 hour |

**Deliverable:** Agent class runs iterations, manages tool registry, hits iteration limit (25 max).

**Unblocks:** Tracks B, C (need Agent interface to mock)

---

### TRACK B: Message & API Layer (Dev 2) — 8-12 hours
**Blocker:** Needs Agent interface from Track A (~1 hour in)

| Task | File | Status |
|---|---|---|
| `Message.java` (request/response structure) | Model | 2 hours |
| `PromptBuilder.java` (message sequence) | Builder logic | 2 hours |
| `Client.java` (HTTP, retry, error handling) | API calls | 3 hours |
| `Config.java` + `SessionLogger.java` | Env + JSONL | 2 hours |

**Deliverable:** End-to-end HTTP requests to Claude; JSONL logs written.

**Unblocks:** Track C (needs Client.java)

---

### TRACK C: All 5 LLM Backends (Devs 3-4, parallel) — 10-14 hours
**Blocker:** Needs Client.java from Track B (~4 hours in)

Assign 2 backends per dev:
- **Dev 3:** Anthropic + OpenAI
- **Dev 4:** Gemini + Ollama (local + cloud)

| Backend | Files | Time/Backend |
|---|---|---|
| Anthropic Claude | `backends/anthropic.rb` | 2.5 hours |
| OpenAI GPT | `backends/openai.rb` | 2.5 hours |
| Google Gemini | `backends/gemini.rb` | 2 hours |
| Ollama (local) | `backends/ollama.rb` | 1.5 hours |
| Ollama Cloud | `backends/ollama_cloud.rb` | 1.5 hours |

**Strategy:** Use factory/strategy pattern. Each backend implements common interface; HTTP call logic reused from Track B.

**Deliverable:** All 5 backends working; agent can switch between them.

---

### TRACK D: Tooling, CLI, DSL (Dev 1 after Track A, ~8 hours later) — 6-8 hours
**Blocker:** Needs working Agent + backends (Tracks A, C complete)

| Task | File | Status |
|---|---|---|
| `RunBuilder.java` (DSL fluent API) | DSL | 2 hours |
| `StandardToolLibrary.java` (predefined tools) | Tools | 2 hours |
| CLI launcher (main entry point) | Executable | 1.5 hours |
| `ReplLoop.java` (interactive shell) | REPL | 1.5 hours |

**Deliverable:** Can run agent from CLI; DSL works; REPL interactive.

---

### TRACK E: Context & Web UI (Deferred — after core is solid) — 10-14 hours
**Start:** After Tracks A-D complete (day 2-3)

| Task | File | Status |
|---|---|---|
| `Context.java` (state management) | Context | 2 hours |
| Spring Boot REST API for logs | log_viz backend | 4 hours |
| Frontend (React/Vue) for log replay | log_viz frontend | 5 hours |
| Integration + e2e tests | Test suite | 3 hours |

**Deliverable:** Web UI for replaying agent sessions.

---

## Timeline Summary

```
Hour 0-1:      [A: Maven setup] [B: Design] [C: Design] [D: Ready]
Hour 1-4:      [A: Agent/Tool] [B: Blocked] [C: Blocked] [D: Blocked]
Hour 4-8:      [A: Tests]      [B: Message/Client] [C: Blocked] [D: Ready]
Hour 8-12:     [A: Done]       [B: Backends ready] [C: Anthropic+OpenAI] [D: DSL]
Hour 12-16:    [A: Track D]    [B: Done]          [C: Gemini+Ollama]  [D: CLI/REPL]
Hour 16-20:    [A+D: Testing & Integration]       [C: Done] [E: Context]
Hour 20-24:    [A+D: Ship MVP] [C+D: Validation]  [E: Web UI setup]
Hour 24-48:    [E: Web UI] [All: Testing, fixes, refinement]
Hour 48-72:    [All: Final validation, deployment]
```

**MVP "Done" at 24 hours:** All backends work, agent loop solid, CLI functional.  
**Full port at 48-72 hours:** Plus context management, web UI, comprehensive tests.

---

## Critical Questions to Answer

Before starting: clarify these items to avoid rework.

### 1. **API Call Response Parsing**
   - **Q:** Does the Ruby code use specific JSON parsing libraries or conventions? Any custom deserialization logic we need to replicate exactly?
   - **Action:** Inspect `lib/boukensha/client.rb` and each backend file for response parsing logic.

### 2. **Error Handling & Retry Semantics**
   - **Q:** What retry conditions, backoff strategies, and error thresholds exist? Must be preserved exactly for behavioral parity.
   - **Action:** Trace all exception handling and retry calls in `client.rb`.

### 3. **Message Format & Tool Invocation**
   - **Q:** Are there subtle differences in how message sequences are constructed across backends? Any implicit state or ordering requirements?
   - **Action:** Compare message building logic in `prompt_builder.rb` across all backends.

### 4. **JSONL Logging Contract**
   - **Q:** Downstream systems (log_viz, monitoring) depend on the JSONL format. What's the exact schema? Are there any undocumented fields or optional structures?
   - **Action:** Sample a few JSONL logs from test runs; verify all fields are documented.

### 5. **Dependency on Ruby Idioms**
   - **Q:** Are there any Ruby-specific behaviors (duck typing, metaprogramming, Enumerable chains) that would be awkward to port directly? Should we refactor those areas or replicate them mechanically?
   - **Action:** Scan for method_missing, define_method, eval, or heavy Enumerable usage.

### 6. **Configuration & Environment Handling**
   - **Q:** Does the Ruby code rely on implicit environment setup, global state, or specific dotenv conventions? What's the initialization contract?
   - **Action:** Review `config.rb` and any initialization scripts.

### 7. **Testing Strategy**
   - **Q:** Are there existing Ruby tests we can use as a reference? Should we port tests in parallel or write them after the port?
   - **Action:** Check for `test/` or `spec/` directories.

### 8. **Spring Boot vs. Plain Java?**
   - **Q:** Should we use Spring Boot (faster setup, built-in dependency injection) or plain Java (fewer dependencies)? This affects build config and package structure.
   - **Answer (tentative):** Spring Boot recommended for faster iteration and dependency injection, unless constraints suggest otherwise.

### 9. **Concurrency Model**
   - **Q:** Does the Ruby code have any threading or async logic? Ruby has different concurrency semantics than Java (GIL vs. true threads).
   - **Action:** Check for Thread, Fiber, or async/await usage.

### 10. **External Tool Integration**
   - **Q:** The `standard_tool_library.rb` may invoke external commands or APIs. What are the integration points? Do they need exact CLI argument parity?
   - **Action:** Document all external invocations and their contracts.

---

## Recommended Java Stack

**Build Tool:** Maven (simpler for a port; Gradle adds learning curve)  
**Web Framework (if needed):** Spring Boot  
**HTTP Client:** OkHttp (simple, robust, no excessive dependencies)  
**JSON:** Jackson (standard, integrated with Spring)  
**Logging:** SLF4J + Logback (then supplement with custom JSONL logger)  
**Environment:** dotenv4j or Spring Cloud Config  
**CLI:** Picocli (lightweight, structured) or Plain java.lang.ProcessBuilder  

---

## Risks & Mitigation

| Risk | Impact | Mitigation |
|---|---|---|
| **Behavioral divergence in API calls** | High | Create integration tests against real APIs (sandbox); compare JSON responses byte-for-byte during Phase 1 |
| **JSONL format drift** | Medium | Freeze JSONL schema early; test log_viz compatibility with Java-generated logs |
| **Backend-specific quirks** | Medium | Port one backend fully before moving to the next; run end-to-end tests for each |
| **Timeline slip** | High | Ruthlessly defer Phase 4 (context, web UI); Phase 1-3 is the MVP |
| **Ruby idioms hard to translate** | Low | Keep a reference copy of Ruby code alongside Java; prioritize clarity over elegance |

---

## Success Criteria

✅ Core agent loop runs with iteration limits enforced  
✅ All 5 LLM backends integrated and tested  
✅ Tool invocation and response handling match Ruby behavior  
✅ JSONL logs generated with exact schema match  
✅ CLI executable works end-to-end (single agent run, multiple iterations)  
✅ All Phase 1 + Phase 2 tests pass (or have a clear test plan)  

---

## Critical Questions (Clarify Now — Blocks Implementation)

**Resolve these BEFORE hour 0; each delay cascades across all tracks.**

### 1. **API Response Parsing & Serialization** ⚠️
   - **Q:** Does `client.rb` use custom JSON parsing, or straight deserialization? Any unusual field handling or transformations?
   - **Impact:** Track B + C (all backends depend on this)
   - **Action:** Read `lib/boukensha/client.rb` lines 1-50, inspect response handling
   - **Answer needed:** Yes/No custom logic → affects Message/Response DTOs

### 2. **Retry Logic & Backoff Semantics** ⚠️
   - **Q:** What are exact retry conditions, backoff strategy (exponential? fixed?), and error thresholds? Must replicate identically.
   - **Impact:** Track B, must be exact before backends test
   - **Action:** Grep `client.rb` for `retry`, `sleep`, `backoff`; trace all exception handling
   - **Answer needed:** Retry count, delay formula, which errors retry vs. fail-fast

### 3. **Message Sequence Construction** ⚠️
   - **Q:** Across backends (Anthropic, OpenAI, Gemini), how do message sequences differ? Any implicit state or ordering?
   - **Impact:** Track B (PromptBuilder.java) + Track C (backend implementations)
   - **Action:** Compare `prompt_builder.rb` usage in each `backends/*.rb` file; note divergences
   - **Answer needed:** Which fields are backend-specific? Any reordering logic?

### 4. **JSONL Logging Contract** ⚠️
   - **Q:** What's the exact JSONL schema? Any fields we can't identify from code inspection? Do downstream tools (log_viz, monitoring) rely on undocumented fields?
   - **Impact:** Track B (SessionLogger.java); affects testing/validation
   - **Action:** Sample 3-5 JSONL logs from Ruby runs; map every field to code; validate with log_viz
   - **Answer needed:** Complete schema + sample output

### 5. **Iteration Limit & Stop Conditions** ⚠️
   - **Q:** Agent max 25 iterations — what ends iterations? Tool response? Token limit? Explicit stop signal?
   - **Impact:** Track A (Agent.java core loop)
   - **Action:** Inspect `agent.rb` iteration loop; identify all exit conditions
   - **Answer needed:** Is it `while iteration_count < 25` or `while @agent.continue?`?

### 6. **Tool Response Handling**
   - **Q:** When a tool is invoked, what's the response format? Does it go directly into next message or through special processing?
   - **Impact:** Track A (Agent) + Track B (PromptBuilder)
   - **Action:** Trace tool invocation → response → next message in `agent.rb`
   - **Answer needed:** Response structure, any transformations before re-prompt

### 7. **Config & Environment Setup**
   - **Q:** Any implicit initialization? Global state? Specific .env variable names required? Order of operations matter?
   - **Impact:** Track A (Config.java startup)
   - **Action:** Read `config.rb` end-to-end; check for side effects or ordering
   - **Answer needed:** Is initialization idempotent? What breaks if config is wrong?

### 8. **Backend Selection & Factory Pattern**
   - **Q:** How does Ruby select which backend to use? Explicit call or implicit via config? Can we use simple factory or do we need reflection/plugins?
   - **Impact:** Track C (backend integration), Track D (DSL)
   - **Action:** Grep for backend selection in `run_dsl.rb` and agent initialization
   - **Answer needed:** Enum-based, string-based, or class-based selection?

### 9. **Error Handling Across Backends**
   - **Q:** Do different backends fail differently? Any backend-specific error codes we need to handle distinctly?
   - **Impact:** Track C (all backends) + Track A (error propagation)
   - **Action:** Compare error handling in each `backends/*.rb` file
   - **Answer needed:** Shared error interface or backend-specific exceptions?

### 10. **Tool Library & Predefined Tools**
   - **Q:** What's in `standard_tool_library.rb`? Are there dependencies on Ruby-specific features (reflection, metaprogramming)?
   - **Impact:** Track D (StandardToolLibrary.java)
   - **Action:** Read `standard_tool_library.rb` in full
   - **Answer needed:** Tool definitions, any runtime generation or metaprogramming?

---

## Blocking Questions — ANSWERED ✅

| Question | Answer | Track |
|---|---|---|
| **Is retry logic custom?** | ✅ Exponential backoff: 0.5 * 2^(n-1) sec; MAX_RETRIES=3; status codes [408,409,429,500,502,503,504] + transient errors | B, C |
| **Exact JSONL schema?** | ✅ Every entry: {session_id, at (ISO8601), phase, ...phase_fields}; Phases: session_start, iteration, prompt, tool_call, tool_result, response, limit_reached, turn_end, raw | B |
| **How to select backend?** | ✅ Symbol-based case: :anthropic, :openai, :gemini, :ollama, :ollama_cloud | C, D |
| **Tool response format?** | ✅ String via context.add_message(:tool_result, result.to_s, tool_use_id: id) | A, B |
| **Max iterations = 25, exactly?** | ✅ MAX_ITERATIONS=25 constant; exits when iteration >= max; wrap_up call outside counted loop | A |

---

## Detailed Answers to All 10 Questions

### Q1: API Response Parsing & Serialization ✅
**Answer:** Simple `JSON.parse(response.body)` — no custom logic.
- Ruby Client just parses raw JSON and returns Hash
- No transformations, no custom deserializers
- Backend's `parse_response()` method normalizes into common shape

**For Java:** Use Jackson ObjectMapper; no custom parsing needed.

---

### Q2: Retry Logic & Backoff Semantics ✅
**Answer:** Exponential backoff with specific config.
```
MAX_RETRIES = 3
BASE_RETRY_DELAY = 0.5 seconds
Backoff formula: 0.5 * 2^(attempt - 1)
  → Attempt 1: 0.5s
  → Attempt 2: 1.0s
  → Attempt 3: 2.0s

Retryable status codes: [408, 409, 429, 500, 502, 503, 504]
Transient errors: EOFError, ECONNRESET, ECONNREFUSED, OpenTimeout, ReadTimeout, SSLError, SocketError, Timeout
```

**For Java:** Use OkHttp with custom Interceptor; implement exponential backoff exactly as above.

---

### Q3: Message Sequence Construction ✅
**Answer:** Backend-specific; each backend transforms messages differently.

**Anthropic:**
- `:tool_result` role → `{ role: "user", content: [{ type: "tool_result", tool_use_id: X, content: Y }] }`
- Other roles → as-is

**OpenAI:**
- System message prepended separately
- `:tool_result` role → `{ role: "tool", tool_call_id: X, content: Y }`
- `:assistant` role → reconstructed from content blocks

**Gemini:**
- `:assistant` role → `{ role: "model", parts: [...] }`
- `:tool_result` role → `{ role: "user", parts: [{ functionResponse: { name: X, response: { content: Y } } }] }`

**For Java:** Each backend class must implement `toMessages()` method with exact transformations above.

---

### Q4: JSONL Logging Contract ✅
**Answer:** Exact schema per phase.

**All entries have:**
- `session_id` (string, e.g. "20260810T120000Z-abc123")
- `at` (ISO8601 timestamp, e.g. "2026-08-10T12:00:00Z")
- `phase` (string)
- Phase-specific fields

**Phases & Fields:**
- `session_start`: task, max_iterations, max_output_tokens, model, provider
- `iteration`: n, max
- `prompt`: message_count, messages (array), tool_count, tools (array)
- `tool_call`: name, args
- `tool_result`: name, result, ok (boolean), error (nullable)
- `response`: text, usage (nullable), stop_reason, task, provider, model, usage_unit, usage_level, input_tokens, output_tokens, cost_usd
- `limit_reached`: kind ("max_iterations"), n, max
- `turn_end`: reason, iterations, tokens (nullable)
- `raw`: data (debug only)

**For Java:** Create POJO per phase; serialize with Jackson; match exact field names.

---

### Q5: Iteration Limit & Stop Conditions ✅
**Answer:** Hard limit at 25 iterations with graceful wind-down.

```ruby
# Loop:
while @iteration < @max_iterations
  @iteration += 1
  response = client.call(...)
  if stop_reason == "tool_use"
    handle_tool_calls(...)
  else
    return text
  end
end

# Then call wrap_up OUTSIDE the loop:
# - Does NOT increment @iteration
# - Does NOT re-check limits
# - Makes one final tool-disabled call
# - Returns text or fallback message
```

**For Java:** Exact replica of this logic; wrap_up is a separate method outside iteration loop.

---

### Q6: Tool Response Handling ✅
**Answer:** Tool result goes directly into message context as-is.

```ruby
result = @registry.dispatch(name, args)  # Returns whatever tool block returns
@context.add_message(:tool_result, result.to_s, tool_use_id: use_id)
```

**For Java:** Store result as string; pass to context as `Message(role=tool_result, content=result.toString(), toolUseId=useId)`.

---

### Q7: Config & Environment Setup ✅
**Answer:** Layered loading; idempotent initialization.

```ruby
# Order:
1. BOUKENSHA_DIR env var (optional override)
2. Falls back to ~/.boukensha
3. Load .env from config_dir
4. Load settings.yaml from config_dir
```

**Initialization is idempotent:** Can call `Config.new` multiple times safely.

**For Java:** Use dotenv4j or Spring Cloud Config; replicate folder/file resolution exactly.

---

### Q8: Backend Selection & Factory Pattern ✅
**Answer:** Symbol-based case statement; 5 backends total.

```ruby
case backend
when :anthropic    then Backends::Anthropic.new(api_key: api_key, model: model)
when :openai       then Backends::OpenAI.new(api_key: api_key, model: model)
when :gemini       then Backends::Gemini.new(api_key: api_key, model: model)
when :ollama       then Backends::Ollama.new(host: ollama_host, model: model)
when :ollama_cloud then Backends::OllamaCloud.new(api_key: api_key, model: model)
else raise ArgumentError, "Unknown backend #{backend.inspect}"
end
```

**For Java:** Use enum for backend type; factory method returns Backend interface implementation.

---

### Q9: Error Handling Across Backends ✅
**Answer:** Shared exception hierarchy; no backend-specific types.

```ruby
class ApiError         < StandardError; end
class UnknownToolError < StandardError; end
class LoopError        < StandardError; end
class UnsupportedModelError < StandardError; end
```

Each backend follows same error contract:
- HTTP errors → `ApiError`
- Missing tools → `UnknownToolError`
- Model validation → `UnsupportedModelError`

**For Java:** Create custom exception classes; throw same exceptions from all backends.

---

### Q10: Tool Library & Predefined Tools ✅
**Answer:** No predefined/standard library; all tools are user-defined.

```ruby
# Tools are registered via blocks:
Boukensha.run(task: "...") do
  tool("name", description: "...", parameters: {...}) { |arg1, arg2| ... }
end

# Tool is a Struct:
Tool = Struct.new(:name, :description, :parameters, :block)

# Registry dispatches:
def dispatch(name, args = {})
  tool = @context.tools[name.to_s]
  raise UnknownToolError unless tool
  tool.block.call(**args.transform_keys(&:to_sym))
end
```

**For Java:** Create Tool POJO; use lambdas/functional interface for block; Registry.dispatch uses reflection/method invocation.

---

## File Structure Template

```
week1_baseline/java/    ← ALL JAVA CODE HERE (isolated from Ruby)
├── pom.xml
├── src/main/java/com/boukensha/
│   ├── Agent.java
│   ├── config/
│   │   └── Config.java
│   ├── api/
│   │   ├── Client.java
│   │   └── backends/
│   │       ├── AnthropicBackend.java
│   │       ├── OpenAIBackend.java
│   │       ├── GeminiBackend.java
│   │       ├── OllamaBackend.java
│   │       └── OllamaCloudBackend.java
│   ├── tool/
│   │   ├── Tool.java
│   │   └── Registry.java
│   ├── model/
│   │   ├── Message.java
│   │   ├── PromptBuilder.java
│   │   └── Context.java
│   ├── logger/
│   │   └── SessionLogger.java
│   ├── dsl/
│   │   └── RunBuilder.java
│   └── repl/
│       └── ReplLoop.java
├── src/test/java/com/boukensha/
│   ├── AgentTest.java
│   ├── api/
│   │   ├── ClientTest.java
│   │   └── backends/
│   │       └── BackendTests.java
│   └── ...
└── src/main/resources/
    └── .env.example

week1_baseline/ruby/    ← READ-ONLY REFERENCE (no changes)
└── [all Ruby files unchanged]
```

**Structure principle:** Complete isolation; Java is self-contained in `week1_baseline/java/` with its own Maven build, tests, and dependencies.

---

## Next Steps — Immediate Actions

### HOUR 0 (RIGHT NOW)
1. **Answer the 5 Blocking Questions** above (non-negotiable; others can parallelize around unknowns)
   - Assign: 1 dev to read `client.rb`, `agent.rb`, `backends/*.rb` files
   - Time budget: 1-2 hours max
   - Output: Fill in the "Blocking Questions" table above

2. **Get sample JSONL log** from a Ruby run
   - Run a full agent iteration in Ruby; save the `.jsonl` file
   - Inspect it; document exact schema

### HOUR 2 (START PARALLEL WORK)
1. **Set up Maven project** (Track A lead)
   - Create `pom.xml`, package structure, Git repo
   - Time: 1 hour

2. **Design Message/Response DTOs** (Track B lead)
   - Based on blocking question answers
   - Time: 1 hour (parallelize with Track A)

3. **Stage Ruby reference files** in Java project
   - Keep `lib/boukensha/client.rb`, `agent.rb`, `backends/` as comments/refs
   - Time: 0.5 hour

### HOURS 3-24 (EXECUTE TRACKS A-D)
See timeline chart above. Each track starts based on blocker status:
- **Track A (Agent):** Start immediately after Maven setup
- **Track B (Message/Client):** Unblocks after blocking Qs answered
- **Track C (Backends):** Unblocks after Client.java is working
- **Track D (CLI/DSL):** Unblocks after Agent + Backends done

### HOURS 24+ (VALIDATION & TRACK E)
- Integration testing across all components
- Deploy Track E (Context + Web UI) if time permits
- Final validation before ship

---

## Appendix: Ruby File Inventory

**Full file list for reference:**

- Core: `agent.rb`, `client.rb`, `tool.rb`, `registry.rb`, `message.rb`, `prompt_builder.rb`, `config.rb`, `logger.rb`
- Backends: `anthropic.rb`, `openai.rb`, `gemini.rb`, `ollama.rb`, `ollama_cloud.rb` (in `backends/`)
- High-level: `run_dsl.rb`, `standard_tool_library.rb`, `repl_loop.rb`, `context.rb`
- CLI: `bin/boukensha`
- Web: `log_viz/` (Sinatra app)
- Tests/examples: Various in `week1_baseline/ruby/` structure

**Total:** 256 .rb files, ~19,865 LOC
