"""
AgentDefinition-based MUD player agent setup.
Defines the tbaMUD player agent programmatically instead of using filesystem.
"""

from anthropic.agents import AgentDefinition, Tool


def create_mud_player_agent() -> AgentDefinition:
    """Create the tbaMUD player agent with AgentDefinition."""

    agent = AgentDefinition(
        name="tbaMUD-player",
        description="Subagent that interacts with the tbaMUD game on localhost:4000. Handles gameplay actions, status checks, exploration, and world navigation.",
        instructions="""You are a player in the tbaMUD game (a MUD running on localhost:4000). Your role is to:

1. Execute gameplay commands sent by the user
2. Interpret and report back the game state and responses
3. Navigate the world of Midgaard based on provided maps and routes
4. Manage character resources and track game state
5. Provide strategic advice based on character capabilities and world knowledge

You control a Warrior character (credentials: dummy / helloworld) or can switch to a Magic user (Smarty / goodbyemoon).

## Game Mechanics
- Use commands like: look, north, south, east, west, kill, cast, drink, eat, buy, sell
- Check character status with: stat, skill, prac, inv (inventory)
- Interact with NPCs by viewing them and trading
- Navigate to different areas using cardinal directions

## Key Information
- You maintain state about the player (health, mana, location, skills, inventory)
- Reference the world map to navigate efficiently
- Track practice sessions (limited resource for skill improvement)
- Note monster types and their locations for combat planning

## Data Files
You have access to persistent memory files:
- player.md: Current character statistics, status, location, and skill info
- world.md: Map of Midgaard, NPC locations, routes, and game mechanics

Use these files to provide context-aware navigation and decision-making across multiple interactions.""",
        tools=[
            Tool(
                name="execute_mud_command",
                description="Execute a single command in the tbaMUD game and return the response",
                input_schema={
                    "type": "object",
                    "properties": {
                        "command": {
                            "type": "string",
                            "description": "The MUD command to execute (e.g., 'look', 'north', 'kill orc', 'cast fireball')",
                        }
                    },
                    "required": ["command"],
                },
            ),
            Tool(
                name="get_player_state",
                description="Read the current player state and statistics from memory",
                input_schema={
                    "type": "object",
                    "properties": {},
                    "required": [],
                },
            ),
            Tool(
                name="update_player_state",
                description="Update the player state and statistics in memory based on game responses",
                input_schema={
                    "type": "object",
                    "properties": {
                        "updates": {
                            "type": "object",
                            "description": "Dictionary of player state updates (e.g., {'location': 'Inn', 'health': 45, 'mana': 20})",
                        }
                    },
                    "required": ["updates"],
                },
            ),
            Tool(
                name="get_world_map",
                description="Retrieve the world map and location information from memory",
                input_schema={
                    "type": "object",
                    "properties": {},
                    "required": [],
                },
            ),
        ],
    )

    return agent
