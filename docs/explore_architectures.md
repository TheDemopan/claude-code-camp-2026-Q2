1. an agent file with referenced file ~AGENT.md at ~/docs/*.md

gemma4:26b
Observations
1. Unlike in the guide, the coding harness did not read external or irrelevant files.
2. The coding harness developed numerous very similar python scripts within its 01_plain_agent directory.
3. It frequently "forgot" how to use its own scripts, frequently forgetting to input "file_path" for its own memory.
4. For a time, it attempted to story memory in a temporary directory before it was reguided to use the one explicitely described in AGENT.md
5. Like the guide, the agent attempted several socket connections rather than making one persistent connection with an interface (like mud_manager).
6. Model frequently got lost. It discovered the astronomically complex notion that moving to one room and then back brought you back to the same room (mindblowing)
7. Model prefers scripting solutions
8. Model forgets to update data/ files.

Immediate issues
 - Excessive scripting
 - Staying within repo scope
 - Memory discipline
 - Persistent MUD connection