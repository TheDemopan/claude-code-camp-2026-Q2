# AgentDefinition Refactoring

## Overview

This directory has been refactored to use `AgentDefinition` from the Claude Agent SDK instead of relying on filesystem-based agent definitions. This provides a more programmatic, type-safe approach to defining and managing agents.

## Changes Made

### Previous Approach (Filesystem-based)
- Agent definition stored in `.claude/agents/tbaMUD-player.md`
- Used markdown frontmatter format to define agent properties
- Relied on the filesystem for agent discovery and configuration
- Less flexible for programmatic agent management

### New Approach (AgentDefinition)
- Agent definition created in Python code using `AgentDefinition` class
- Located in `scripts/agent_definition.py`
- Fully programmatic and type-safe
- Can be easily modified, versioned, and managed in code
- Supports multiple agent definitions in the same codebase

## Key Files

### `scripts/agent_definition.py`
Defines the MUD player agent using `AgentDefinition`:
- Creates agent with name, description, and instructions
- Defines all available tools with their schemas
- Supports:
  - `execute_mud_command`: Execute commands in the MUD
  - `get_player_state`: Read player statistics from memory
  - `update_player_state`: Update player state after actions
  - `get_world_map`: Retrieve the game map and location data

### `scripts/server.py`
Updated to use the programmatic agent definition:
- `MUDAgentServer` class manages the agent lifecycle
- Builds system prompts dynamically from `AgentDefinition`
- Processes tool calls and updates game state
- Can be imported and used as a library

### `scripts/agent_client.py`
Example client demonstrating how to use the agent:
- Shows how to initialize and interact with the agent
- Handles tool call processing
- Can be used standalone for testing

## Usage

### Basic Usage with Server

```python
from scripts.server import MUDAgentServer

server = MUDAgentServer()
result = server.run_agent("What is my current status?")
print(result)
```

### Programmatic Agent Definition

```python
from scripts.agent_definition import create_mud_player_agent

agent = create_mud_player_agent()
print(f"Agent: {agent.name}")
print(f"Description: {agent.description}")
print(f"Available tools: {[tool.name for tool in agent.tools]}")
```

### Extending with Custom Agents

To create additional agents, extend the pattern in `agent_definition.py`:

```python
from anthropic.agents import AgentDefinition, Tool

def create_custom_agent() -> AgentDefinition:
    agent = AgentDefinition(
        name="my-agent",
        description="My custom agent",
        instructions="Agent behavior instructions...",
        tools=[
            Tool(
                name="my_tool",
                description="Tool description",
                input_schema={...}
            )
        ]
    )
    return agent
```

## Benefits

1. **Type Safety**: Python code provides type hints and IDE support
2. **Version Control**: Agent definitions are in git like any other code
3. **Testability**: Easy to unit test agent configurations
4. **Flexibility**: Can programmatically generate agents based on runtime conditions
5. **Maintainability**: Single source of truth for agent configuration
6. **Scalability**: Support multiple agents in the same codebase

## Migration Path

If you have other agents defined in `.claude/agents/`, you can migrate them by:

1. Create a new function in `agent_definition.py` that returns an `AgentDefinition`
2. Update the corresponding server or client to use the new definition
3. Remove the old markdown definition file

## Next Steps

- Add proper agent registry/factory pattern for managing multiple agents
- Implement persistent agent state management
- Add configuration validation and error handling
- Create unit tests for agent definitions and tool implementations
