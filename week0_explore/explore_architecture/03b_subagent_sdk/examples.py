"""
Example usage patterns for the AgentDefinition-based MUD player agent.
Demonstrates different ways to interact with the agent.
"""

from scripts.agent_definition import create_mud_player_agent
from scripts.server import MUDAgentServer


def example_1_basic_status():
    """Example 1: Get current player status."""
    print("=" * 60)
    print("Example 1: Get Current Player Status")
    print("=" * 60)

    server = MUDAgentServer()
    result = server.run_agent("What is my current status in the game?")
    print(result)
    print()


def example_2_navigate():
    """Example 2: Navigate to a location."""
    print("=" * 60)
    print("Example 2: Navigate to a Location")
    print("=" * 60)

    server = MUDAgentServer()
    result = server.run_agent(
        "Navigate to the Baker's shop and check what they're selling."
    )
    print(result)
    print()


def example_3_combat():
    """Example 3: Engage in combat."""
    print("=" * 60)
    print("Example 3: Engage in Combat")
    print("=" * 60)

    server = MUDAgentServer()
    result = server.run_agent(
        "I want to fight a rat. Help me locate one and engage in combat."
    )
    print(result)
    print()


def example_4_inspect_agent():
    """Example 4: Inspect the agent definition."""
    print("=" * 60)
    print("Example 4: Inspect Agent Definition")
    print("=" * 60)

    agent = create_mud_player_agent()

    print(f"Agent Name: {agent.name}")
    print(f"Description: {agent.description}")
    print(f"\nInstructions:\n{agent.instructions}")
    print(f"\nAvailable Tools:")
    for tool in agent.tools:
        print(f"  - {tool.name}: {tool.description}")
        print(f"    Input schema: {tool.input_schema}")
    print()


def example_5_multi_step_quest():
    """Example 5: Multi-step quest assistance."""
    print("=" * 60)
    print("Example 5: Multi-Step Quest Assistance")
    print("=" * 60)

    server = MUDAgentServer()
    result = server.run_agent(
        """I want to complete the following tasks in order:
        1. Check my current inventory
        2. Find a shop that sells potions
        3. Buy at least one healing potion
        4. Report back my new inventory

        Guide me through this step by step."""
    )
    print(result)
    print()


def example_6_world_exploration():
    """Example 6: Explore and learn about the world."""
    print("=" * 60)
    print("Example 6: World Exploration")
    print("=" * 60)

    server = MUDAgentServer()
    result = server.run_agent(
        """Using the world map, tell me:
        1. Where are the main shops in Midgaard?
        2. What NPCs should I interact with?
        3. What are good hunting grounds for my character level?"""
    )
    print(result)
    print()


def example_7_skill_training():
    """Example 7: Skill training and practice."""
    print("=" * 60)
    print("Example 7: Skill Training")
    print("=" * 60)

    server = MUDAgentServer()
    result = server.run_agent(
        "What skills should I practice? How many practice sessions do I have left?"
    )
    print(result)
    print()


def example_8_agent_initialization():
    """Example 8: Different ways to initialize the agent."""
    print("=" * 60)
    print("Example 8: Agent Initialization Patterns")
    print("=" * 60)

    # Pattern 1: Direct AgentDefinition access
    agent_def = create_mud_player_agent()
    print(f"Pattern 1 - Direct access: {agent_def.name}")

    # Pattern 2: Through server initialization
    server = MUDAgentServer()
    print(f"Pattern 2 - Through server: {server.agent_def.name}")

    # Pattern 3: Programmatic tool inspection
    tool_names = [tool.name for tool in agent_def.tools]
    print(f"Pattern 3 - Tool inspection: {tool_names}")

    print()


if __name__ == "__main__":
    import sys

    if len(sys.argv) > 1:
        example_num = sys.argv[1]
        if example_num == "1":
            example_1_basic_status()
        elif example_num == "2":
            example_2_navigate()
        elif example_num == "3":
            example_3_combat()
        elif example_num == "4":
            example_4_inspect_agent()
        elif example_num == "5":
            example_5_multi_step_quest()
        elif example_num == "6":
            example_6_world_exploration()
        elif example_num == "7":
            example_7_skill_training()
        elif example_num == "8":
            example_8_agent_initialization()
        else:
            print(f"Unknown example: {example_num}")
    else:
        print("Usage: python examples.py <example_number>")
        print("Available examples:")
        print("  1 - Get current player status")
        print("  2 - Navigate to a location")
        print("  3 - Engage in combat")
        print("  4 - Inspect agent definition")
        print("  5 - Multi-step quest assistance")
        print("  6 - World exploration")
        print("  7 - Skill training and practice")
        print("  8 - Agent initialization patterns")
