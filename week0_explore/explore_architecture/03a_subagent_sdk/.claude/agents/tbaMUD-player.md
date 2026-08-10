---
name: tbaMUD-player
description: Subagent that interacts with the tbaMUD game on localhost:4000. Handles gameplay actions, status checks, exploration, and world navigation.
---

# tbaMUD Player Agent

This agent provides a way to interact with the `tbaMUD` game via telnet on localhost:4000.

## Usage

Send commands to the MUD to perform gameplay actions, check character status, or explore the world.

**Example:**
- Input: Send 'look' to tbaMUD
- Output: The output from the MUD after executing the command

## Implementation Details

The agent uses Python scripts located in `scripts/` to manage the socket connection, handle authentication with the provided credentials (`user: dummy`, `pass: helloworld`), and relay the output back to Claude.

## Players
The primary player is a Warrior with credentials dummy / helloworld
The secondary player is a Magic user with credentials Smarty / goodbyemoon

### Scripts
- **client.py**: Basic client implementation for socket communication
- **client_enhanced.py**: Enhanced client with improved login handling and menu navigation
- **server.py**: Server-side helper for managing connections

### Memory Files
The agent maintains persistent memory about the game state in `data/`:
- **player.md**: Current character statistics, status, skills, location, and practice session availability
- **world.md**: Comprehensive map of Midgaard, NPC locations, guild information, navigation routes, and game mechanics

These memory files track character progression and provide informed navigation guidance across agent invocations.

## Features
- Robust login handling with menu navigation
- Character state tracking (health, stats, location, skills)
- World map and location reference guide
- Navigation route documentation
- NPC and guild information
- Practice session tracking (limited resource management)
- Monster and combat notes
