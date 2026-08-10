"""
Client for the MUD player agent using AgentDefinition.
Demonstrates how to use the programmatic agent definition instead of filesystem-based approach.
"""

import json
import sys
import os
from pathlib import Path
from anthropic import Anthropic
from agent_definition import create_mud_player_agent
from client_enhanced import interact_with_menu


def get_data_dir():
    """Get the data directory path."""
    script_dir = Path(__file__).parent
    return script_dir.parent / "data"


def read_player_state():
    """Read player state from file."""
    data_dir = get_data_dir()
    player_file = data_dir / "player.md"
    if player_file.exists():
        return player_file.read_text()
    return "No player state found."


def read_world_map():
    """Read world map from file."""
    data_dir = get_data_dir()
    world_file = data_dir / "world.md"
    if world_file.exists():
        return world_file.read_text()
    return "No world map found."


def update_player_state(updates: dict):
    """Update player state in file."""
    data_dir = get_data_dir()
    player_file = data_dir / "player.md"
    current = read_player_state()

    # Simple update - in production this would be more sophisticated
    for key, value in updates.items():
        current += f"\n- {key}: {value}"

    player_file.write_text(current)
    return f"Updated player state: {updates}"


def execute_mud_command(command: str) -> str:
    """Execute a command in the MUD."""
    try:
        result = interact_with_menu(command)
        return result
    except Exception as e:
        return f"Error executing command: {e}"


def process_tool_call(tool_name: str, tool_input: dict) -> str:
    """Process tool calls from the agent."""
    if tool_name == "execute_mud_command":
        return execute_mud_command(tool_input.get("command", ""))
    elif tool_name == "get_player_state":
        return read_player_state()
    elif tool_name == "get_world_map":
        return read_world_map()
    elif tool_name == "update_player_state":
        return update_player_state(tool_input.get("updates", {}))
    else:
        return f"Unknown tool: {tool_name}"


def run_agent_conversation(user_message: str):
    """Run a conversation with the MUD player agent."""
    client = Anthropic()
    agent_def = create_mud_player_agent()

    messages = [{"role": "user", "content": user_message}]

    print(f"User: {user_message}\n")

    while True:
        # Create a message with the agent definition
        response = client.messages.create(
            model="claude-opus-4-1-20250805",
            max_tokens=4096,
            system=f"""You are a subagent defined by AgentDefinition.
Name: {agent_def.name}
Description: {agent_def.description}

{agent_def.instructions}

Available tools: {', '.join(tool.name for tool in agent_def.tools)}""",
            tools=[
                {
                    "name": tool.name,
                    "description": tool.description,
                    "input_schema": tool.input_schema,
                }
                for tool in agent_def.tools
            ],
            messages=messages,
        )

        # Check if we're done (no more tool calls)
        if response.stop_reason == "end_turn":
            # Extract and print final text response
            for block in response.content:
                if hasattr(block, "text"):
                    print(f"Agent: {block.text}")
            break

        # Process tool calls
        tool_calls_made = False
        for block in response.content:
            if block.type == "tool_use":
                tool_calls_made = True
                tool_name = block.name
                tool_input = block.input

                print(f"[Tool: {tool_name}]")
                print(f"  Input: {json.dumps(tool_input, indent=2)}")

                # Execute the tool
                result = process_tool_call(tool_name, tool_input)
                print(f"  Result: {result[:200]}...")  # Print first 200 chars

                # Add assistant response and tool result to messages
                messages.append({"role": "assistant", "content": response.content})
                messages.append(
                    {
                        "role": "user",
                        "content": [
                            {
                                "type": "tool_result",
                                "tool_use_id": block.id,
                                "content": result,
                            }
                        ],
                    }
                )
                break  # Process one tool call at a time

        if not tool_calls_made:
            # No more tool calls and not end_turn - print remaining content
            for block in response.content:
                if hasattr(block, "text"):
                    print(f"Agent: {block.text}")
            break


if __name__ == "__main__":
    user_input = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else "What is my current status?"
    run_agent_conversation(user_input)
