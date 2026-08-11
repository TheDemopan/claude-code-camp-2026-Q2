## Memory

The agent maintains persistent structured memory under the `memory/` directory. This memory is used to remember the tbaMUD world across sessions and should become progressively more complete through exploration.

The files in `memory/` are authoritative for persistent world knowledge. Do not place the full map directly in this document.

### Directory Structure

```text
memory/
├── map.csv
├── rooms.jsonl
├── state.json
└── notes.md
```

Each file has a specific purpose and should not duplicate information unnecessarily.

---

### `memory/map.csv`

This file contains the navigation graph.

Each row represents one known room and its confirmed exits.

Schema:

```text
id,area,name,n,e,s,w,u,d,x,y,z
```

Fields:

* `id` — unique numeric room ID assigned by the agent.
* `area` — MUD zone or area name.
* `name` — room title.
* `n,e,s,w,u,d` — destination room IDs for confirmed exits.
* `?` means the exit exists but has not yet been explored.
* Empty means no known exit.
* `x,y,z` — optional coordinates for visualization only.

Example:

```text
id,area,name,n,e,s,w,u,d,x,y,z
1024,Midgaard,Temple Square,1025,,,,,,12,8,0
1025,Midgaard,Temple Entrance,1027,1026,1024,?,,,12,9,0
1026,Midgaard,Temple Hall,,,,1025,,,13,9,0
```

The exit graph is authoritative.

Never infer connections solely from coordinates.

Do not assume every exit has a reverse exit.

---

### `memory/rooms.jsonl`

This file stores verbose room information.

It is newline-delimited JSON (JSONL), with one JSON object per room.

Example:

```json
{"id":1024,"description":"A broad stone square surrounds the temple.","landmarks":["temple","fountain"],"npcs":[],"items":[]}
{"id":1025,"description":"You stand at the entrance to the temple.","landmarks":["temple doors"],"npcs":[],"items":[]}
```

Fields may include:

* `id`
* `description`
* `landmarks`
* `npcs`
* `items`
* `shops`
* `notes`

Only store information that is likely to remain useful.

Do not duplicate room names, areas, or exits here; those belong in `map.csv`.

---

### `memory/state.json`

This file stores the agent's current state.

Example:

```json
{
  "current_room": 1025,
  "previous_room": 1024,
  "recent_path": [1024,1025],
  "current_objective": "Find the bank",
  "unexplored_exits": ["1025:w"]
}
```

Maintain:

* current room
* previous room
* recent path
* current objective
* known unexplored exits

This file represents current working state rather than permanent world knowledge.

---

### `memory/notes.md`

This file contains durable high-level knowledge that does not naturally belong to a specific room.

Examples:

* Important NPCs.
* Quest mechanics.
* Transportation systems.
* Dangerous areas.
* Useful commands.
* Global observations about the MUD.

Keep this concise.

Do not use it as a dump for room descriptions.

---

## Room Identification

When entering a room:

1. Check whether the room is already known.
2. Compare stable observations:

   * room name
   * area
   * available exits
   * distinctive landmarks
   * permanent room description
3. Reuse an existing room ID when there is sufficient evidence that it is the same room.
4. Only create a new room ID when the room is genuinely new.

Do not create duplicate rooms because of minor wording differences in descriptions.

If identity is uncertain, preserve the uncertainty rather than inventing a connection.

---

## Mapping Rules

When a room reveals exits:

* Record every observed exit.
* If an exit has not yet been traversed, mark it as `?`.
* When traversing an unexplored exit:

  1. identify or create the destination room,
  2. update the source room's exit,
  3. update `current_room`,
  4. record the reverse exit only if it is confirmed by the game.

Never assume that moving north and then south returns to the same room.

Doors, portals, one-way passages, elevators, and other special exits may exist.

---

## Coordinates

Coordinates may be assigned to rooms to aid visualization and local spatial reasoning.

They are optional metadata.

They must never override observed exits or be used to invent connections.

The navigation graph in `map.csv` is always authoritative.

---

## Exploration

Unknown exits should be treated as exploration opportunities.

Example:

```text
1025,Midgaard,Temple Entrance,1027,1026,1024,?,,,
```

Here the west exit exists but is unexplored.

When practical, maintain `unexplored_exits` in `state.json` so the agent can systematically expand the map.

Remove an entry once that exit has been explored or determined to be unusable.

---

## Memory Discipline

Use structured memory rather than prose whenever possible.

* `map.csv` — topology.
* `rooms.jsonl` — room-specific observations.
* `state.json` — current state.
* `notes.md` — durable global knowledge.

Do not duplicate facts across files.

Do not invent room IDs, exits, coordinates, or room identities.

If new observations contradict the existing map, treat the map as potentially incorrect and investigate before replacing established information.

Only load or reason about the subset of the map relevant to the current location or objective when possible. The persistent files may grow very large over time; they are the long-term memory store, not all of which needs to be present in the active context at once.
