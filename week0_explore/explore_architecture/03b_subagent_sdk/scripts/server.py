"""
Server for managing the MUD player agent with AgentDefinition.
Uses programmatic agent definition instead of filesystem-based YAML/markdown.
"""

import json
from pathlib import Path
from typing import Any
from anthropic import Anthropic
from agent_definition import create_mud_player_agent
from client_enhanced import interact_with_menu


class MUDAgentServer:
    """Server that manages the MUD player agent using AgentDefinition."""

    def __init__(self):
        self.client = Anthropic()
        self.agent_def = create_mud_player_agent()
        self.conversation_history = []
        self.data_dir = Path(__file__).parent.parent / "data"

    def get_player_state(self) -> str:
        """Read player state from file."""
        player_file = self.data_dir / "player.md"
        if player_file.exists():
            return player_file.read_text()
        return "No player state found."

    def get_world_map(self) -> str:
        """Read world map from file."""
        world_file = self.data_dir / "world.md"
        if world_file.exists():
            return world_file.read_text()
        return "No world map found."

    def update_player_state(self, updates: dict) -> str:
        """Update player state in file."""
        player_file = self.data_dir / "player.md"
        current = self.get_player_state()
        for key, value in updates.items():
            current += f"\n- {key}: {value}"
        player_file.write_text(current)
        return f"Updated player state: {updates}"

    def execute_mud_command(self, command: str) -> str:
        """Execute a command in the MUD."""
        try:
            result = interact_with_menu(command)
            return result
        except Exception as e:
            return f"Error executing command: {e}"

    def process_tool_call(self, tool_name: str, tool_input: dict) -> str:
        """Process tool calls from the agent."""
        if tool_name == "execute_mud_command":
            return self.execute_mud_command(tool_input.get("command", ""))
        elif tool_name == "get_player_state":
            return self.get_player_state()
        elif tool_name == "get_world_map":
            return self.get_world_map()
        elif tool_name == "update_player_state":
            return self.update_player_state(tool_input.get("updates", {}))
        else:
            return f"Unknown tool: {tool_name}"

    def run_agent(self, user_message: str) -> str:
        """Run the agent with a user message and return the final response."""
        messages = [{"role": "user", "content": user_message}]

        while True:
            response = self.client.messages.create(
                model="claude-opus-4-1-20250805",
                max_tokens=4096,
                system=self._build_system_prompt(),
                tools=self._build_tool_definitions(),
                messages=messages,
            )

            if response.stop_reason == "end_turn":
                for block in response.content:
                    if hasattr(block, "text"):
                        return block.text

            tool_calls_made = False
            for block in response.content:
                if block.type == "tool_use":
                    tool_calls_made = True
                    result = self.process_tool_call(block.name, block.input)
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
                    break

            if not tool_calls_made:
                for block in response.content:
                    if hasattr(block, "text"):
                        return block.text
                break

    def _build_system_prompt(self) -> str:
        """Build the system prompt from AgentDefinition."""
        return f"""You are a subagent defined by AgentDefinition.
Name: {self.agent_def.name}
Description: {self.agent_def.description}

{self.agent_def.instructions}

Available tools: {', '.join(tool.name for tool in self.agent_def.tools)}"""

    def _build_tool_definitions(self) -> list[dict[str, Any]]:
        """Build tool definitions from AgentDefinition for the API."""
        return [
            {
                "name": tool.name,
                "description": tool.description,
                "input_schema": tool.input_schema,
            }
            for tool in self.agent_def.tools
        ]


if __name__ == "__main__":
    import sys

    server = MUDAgentServer()
    user_input = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else "What is my current status?"
    result = server.run_agent(user_input)
    print(result)
