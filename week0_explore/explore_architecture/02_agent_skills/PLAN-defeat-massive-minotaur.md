# Plan: Grow Character to Defeat the Massive Minotaur (Newbie Zone)

## Context

The user wants a persistent, trackable long-term goal for the tbaMUD character: defeat **the Massive Minotaur** somewhere in the **Newbie Zone**. This is a multi-session progression goal, not a single action — the character is currently Level 1 (23 HP, `kick` skill rated "bad", 0 gold, no documented equipment), so nothing is known yet about the Minotaur's difficulty, location, or what "growth" is required to beat it.

`data/player.md` is the designated place to track this goal and its subgoals so progress survives across sessions (per the existing memory-maintenance requirement already saved in user memory). `data/world.md` will be enriched with anything discovered about the Newbie Zone / Minotaur / combat mechanics along the way, since none of that is documented yet.

Combat commands (`kill`, `flee`, `consider`, `score`, `wear`, `wield`) are **not yet documented** in our memory — the explore pass over the skill confirmed only `practice`, movement, and NPC-shop interactions (`list`) are known. Part of this plan is deliberately front-loaded reconnaissance to learn these before any fighting.

## Approach

### 1. Add a "Goals" section to `player.md`
Insert a new `## Goals` section (checklist format) tracking:
- **Main Goal**: Defeat the Massive Minotaur (Newbie Zone) — status: Not Started
- **Subgoals** (ordered, checkbox-style, each updated with findings as completed):
  1. Learn core combat/info commands (`consider`, `score`, `kill`, `flee`, `wear`, `wield`) — document usage in `world.md`
  2. Locate the Newbie Zone and the Massive Minotaur; use `consider` against it to gauge relative difficulty without engaging
  3. Gain levels safely on known low-risk monsters (fido, gelatinous blob, sewer rat, small spider — already logged in `world.md`) until `consider` shows the Minotaur as a fair fight
  4. Earn gold via kills/loot and equip weapon + armor from the Weapon Shop / General Store to raise AC and damage
  5. Continue practicing `kick` (and any newly learned skills) past "bad" proficiency using guild practice sessions
  6. Manage hunger/thirst and stock healing/consumables before any serious fight
  7. Engage the Minotaur only when prepared; retreat (`flee`) if HP drops critically; log outcome and iterate

This mirrors the structure already used elsewhere in `player.md` (Character Info / Stats / Skills / Notes) so it stays consistent with the existing file conventions.

### 2. Reconnaissance-first execution order (post-plan, in normal mode)
Once out of plan mode, the actual play session should:
1. Use `client_enhanced.py` (the menu-aware script — `client.py` breaks on the login menu, per the explore pass) to check `score`, `consider`, and `help` for combat commands, and record findings into `world.md` immediately.
2. Search for the Newbie Zone / Massive Minotaur (likely reachable from Midgaard via an unexplored exit — none of the currently mapped routes mention it). Update `world.md`'s map as new areas are found.
3. Only after location + `consider` difficulty are known, fill in realistic level/gear targets in the `player.md` Goals section (replacing generic subgoals with concrete numbers, e.g. "reach level 5" or "need +10 AC").

### 3. Ongoing memory discipline
Per the existing feedback memory (`tbamud-memory-maintenance`), `player.md` and `world.md` must be updated after every meaningful in-game action during execution — not batched at the end — so the goal tracker stays accurate turn-to-turn (HP/level/gold changes, new subgoal completions, new world knowledge).

## Files to change
- `/home/thedemopan/claude-code-camp-2026-Q2/week0_explore/explore_architecture/02_agent_skills/.claude/skills/tbaMUD-player/data/player.md` — add `## Goals` section as described above.
- `/home/thedemopan/claude-code-camp-2026-Q2/week0_explore/explore_architecture/02_agent_skills/.claude/skills/tbaMUD-player/data/world.md` — updated incrementally during execution as the Newbie Zone/Minotaur/combat commands are discovered (no changes needed at plan-approval time; this happens during gameplay).

## Verification
- After editing, `player.md` should render a checklist under `## Goals` with the main goal and 7 subgoals, matching the existing markdown style in the file.
- During subsequent gameplay turns, confirm `consider <minotaur>` output and `score` output get logged into `world.md`/`player.md` respectively before any combat is attempted.
