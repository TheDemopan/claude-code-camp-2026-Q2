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

The skill uses a Python script located in `.claude/skills/tbaMUD-player/scripts/client.py` to manage the socket connection, handle authentication with the provided credentials (`user: dummy`, `pass: helloworld`), and relay the output back to Claude.
