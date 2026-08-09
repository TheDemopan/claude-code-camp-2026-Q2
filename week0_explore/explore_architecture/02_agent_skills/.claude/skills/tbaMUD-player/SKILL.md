---
name: tbaMUD-player
description: Use this skill whenever you want to interact with the tbaMUD game on localhost:4000. This skill allows sending commands and receiving the output from the MUD environment. Make sure to use this skill for any gameplay actions, checking status, or exploring the MUD world.
compatibility: python3
---

# tbaMUD-player

This skill provides a way to interact with the `tbaMUD` game via telnet on localhost:4000. 

## Usage

To send a command to the MUD, you can simply ask me to "run a command in tbaMUD" or "send [command] to tbaMUD". 

**Example:**
Input: Send 'look' to tbaMUD
Output: (The output from the MUD after executing the 'look' command)

## Implementation Details

The skill uses Python scripts located in `.claude/skills/tbaMUD-player/scripts/` to manage the socket connection, handle authentication with the provided credentials (`user: dummy`, `pass: helloworld`), and relay the output back to Claude.

### Scripts
- **client.py**: Original basic client implementation
- **client_enhanced.py**: Enhanced client with improved login handling and menu navigation

### Memory Files
The skill maintains persistent memory about the game state in `.claude/skills/tbaMUD-player/data/`:
- **player.md**: Current character statistics, status, skills, location, and practice session availability
- **world.md**: Comprehensive map of Midgaard, NPC locations, guild information, navigation routes, and game mechanics

These memory files allow the skill to track character progression and provide informed navigation guidance across sessions.

## Features
- Robust login handling with menu navigation
- Character state tracking (health, stats, location, skills)
- World map and location reference guide
- Navigation route documentation
- NPC and guild information
- Practice session tracking (limited resource management)
- Monster and combat notes
