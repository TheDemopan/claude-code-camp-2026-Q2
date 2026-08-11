# Memory Implementation — Java Port

Companion to [`memory_implementation.md`](memory_implementation.md).

That document specifies the memory **schema**. This one specifies how the Java port
**implements and enforces** it.

---

## Context

The Java port currently has no memory. The only thing persisting across turns is the
conversation transcript in `Context`, and `compactMessages()` drops the *oldest* 40% first —
precisely the early exploration needed to navigate back. Session JSONL logs are written but
never read. Nothing survives a process restart.

The schema document enforces its rules through *instructions to a model*.
[`docs/journal/0_preweek.md`](../../journal/0_preweek.md) records that approach failing on both
gemma4:26b and haiku 4.5: memory-discipline breakdowns, forgotten parameters, forgotten file
updates, and eventually hallucinated rooms.

**This plan keeps the schema unchanged and replaces the enforcement mechanism.** Java owns the
files. Topology is captured automatically from MUD output, so the model cannot forget to map.
The model receives tools only for things that genuinely need judgment.

It also adds **route-finding**, which removes the need for the model to do spatial reasoning at
all — the weakest capability in the journal's findings.

**Decisions taken:** auto-capture enforcement; memory stored in `week1_baseline/java/memory/`
(Java-only, self-contained).

---

## Parser format — verified against real captured output

Confirmed from `.boukensha/sessions/*.jsonl` tool results, not assumed:

```
\x1b[0;33mThe Bar Of Swordsmen\x1b[0m\r\n     ← name: first line, colour 0;33
   The bar of swordsmen, once upon a...\r\n  ← description: 3-space indent, wrapped
\x1b[0;36m[ Exits: s w ]\x1b[0m\r\n           ← exits: colour 0;36, stable anchor
A waiter is here.\r\n                        ← contents
23H 100M 84V >                               ← prompt: HP / mana / movement
```

Strip ANSI with `\x1b?\[[0-9;]*m`; lines end `\r\n`. The prompt line yields HP, mana, and
movement for free on every command — capture it into `state.json`.

---

## Implementation

### New package `com.boukensha.memory`

| File | Responsibility |
|---|---|
| `RoomParser.java` | ANSI strip → `ParsedRoom(name, description, exits, contents, hp/mana/mv)`. Returns an empty `Optional` for dark rooms, combat spam, and non-room output. |
| `RoomGraph.java` | `map.csv` in memory: id → room, exits (`?` = known-but-unexplored, empty = none). BFS `findRoute(fromId, toId)` returning a direction list. |
| `RoomDetail.java` | One `rooms.jsonl` record: id, description, landmarks, npcs, items, shops, notes. |
| `AgentState.java` | `state.json`: currentRoom, previousRoom, recentPath, currentObjective, unexploredExits. |
| `MemoryStore.java` | Owns all four files under `week1_baseline/java/memory/`. Load on construct, atomic save, schema validation. Never duplicates facts across files. |
| `MemoryRecorder.java` | The auto-capture hook: given a command and its raw response, parse and fold into the store. |

### Room identification — follow the schema doc exactly

Match candidates on **name + area + exit-set**. Reuse an existing id when those agree; create a
new id only when the room is genuinely new. Minor description wording differences must **not**
create duplicates. When identity is uncertain, preserve the uncertainty rather than inventing a
connection.

**Never auto-add reverse exits.** Record `source --dir--> dest` only for the direction actually
traversed. This is explicit in the schema doc and is the difference between a map and a guess.

### Auto-capture wiring

`MudTools.send(...)` already funnels every command through a single lambda wrapper. Hook there:

- After `readUntilPrompt()`, hand `(command, response)` to `MemoryRecorder`.
- On a successful `move <dir>`: identify or create the destination, set
  `source.exits[dir] = destId`, update `currentRoom` / `previousRoom` / `recentPath`, and drop the
  now-explored entry from `unexploredExits`.
- On `look` with no target: refresh the current room's detail and exit set.
- Everything else: ignore, but still capture HP/mana/movement from the prompt line.

Model discipline required for topology: **none**.

### Memory tools — judgment only (`tools/MemoryTools.java`)

Follows the existing `FileSystemTools.register(registry, …)` static-register pattern.

| Tool | Purpose |
|---|---|
| `set_objective` | Write `current_objective` into `state.json` |
| `remember_note` | Append durable global knowledge to `notes.md` |
| `recall_room` | Look up a room by name or id → description, npcs, items, exits |
| `find_route` | BFS over `map.csv` → `"n, n, w"`. **The key capability** — replaces model spatial reasoning |
| `map_summary` | Nearby topology plus nearest unexplored exits, bounded output |

### Startup context injection

`Repl` / `Boukensha` prepend a compact brief to the system prompt: current room, objective, recent
path, nearest unexplored exits. **Bounded** — the schema doc is explicit that the store may grow
large and only the relevant subset should enter context. Never inline the full map.

### Files to modify

- `tools/MudTools.java` — accept an optional `MemoryRecorder`; invoke it from `send(...)`
- `Boukensha.java` — register `MemoryTools` inside `registerStandardTools(...)` when MUD tools are
  enabled; build the startup brief
- `examples/Step08TheReplLoop.java` — construct the `MemoryStore` and pass it through
- `bin/13_memory` + `examples/Step13Memory.java` — demo launcher printing the accumulated map,
  current state, and a sample route

---

## Verification

1. **Parser against real data.** Extract the captured room strings already present in
   `.boukensha/sessions/*.jsonl` and assert `RoomParser` yields the correct name, exits, and
   description. Real server output, not fixtures — the highest-value test here.
2. **Store round-trip.** Write map / rooms / state / notes, reload, assert equality. Assert
   malformed CSV rows are rejected rather than silently accepted.
3. **Route-finding.** Build a known graph; assert BFS returns the correct direction sequence, and
   returns empty (not a fabricated path) when no route exists.
4. **Live walk.** `./bin/boukensha`, move Yard → Bar → back. Inspect `memory/map.csv` and
   `memory/state.json` by hand: two rooms, correct exits, no invented reverse edges.
5. **Cross-session persistence.** Exit the REPL. Restart. Ask "where am I and what have I
   mapped?" — it must answer from `memory/` without calling a single MUD tool to rediscover it.
   **This is the acceptance test for the whole feature.**
6. **Route in anger.** From the Bar, ask it to return to the Practice Yard. It should call
   `find_route` and follow the result rather than guessing directions.
7. **No regressions.** `./bin/boukensha com.boukensha.examples.SmokeTest` (17 assertions) and
   offline steps `00`–`03` still pass.

---

## Risks

- **Parser brittleness.** Dark rooms, combat interleaving, `[ Exits: None! ]`, and async chatter
  can all corrupt a naive parse. Mitigation: `RoomParser` returns `Optional.empty()` on anything
  it does not confidently recognise, and the recorder writes nothing rather than junk. A missing
  map entry is recoverable; a wrong one poisons navigation.
- **Room-identity collisions.** tbaMUD has many identically-named rooms ("A dark alley"). The
  name + area + exit-set key reduces but does not eliminate this. Prefer creating a duplicate
  over merging two distinct rooms — the schema doc's "preserve the uncertainty" rule.
- **Scope.** This is new design, not a port. None of it exists in the Ruby original, so there is
  no reference implementation to check behaviour against.
