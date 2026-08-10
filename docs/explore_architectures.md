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
 - EXTEMELY poor spatial reasoning.


2. Agent skills driven by main agent.

gemma4:26b
Observations
1. Model had some trouble recognizing the "skills" feature in the harness, had to familiarize itself post install
2. Forgetting function/script parameters was almost entirely present, beyond frequently.
3. ***IMPORTANT*** After some time, the model stopped connecting to the MUD and began hallucinating rooms claiming it was connected to the server. Directly lying about its discoveries
4. Spatial reasoning so poor the agent had to be manually teleported to the Temple of Midgaard.
5. Logic so poor the agent attempted to look down a dark alley to look for a baker.

haiku 4.5
Observations
 1. Much stronger spatial understanding. Found the baker in minutes from the Temple of Midgaard.
 2. Recoded the scripts for relatively smoother connection and command interpretation experience
 3. Developed a plan to fight the minotaur (see PLAN-defeat-massive-minotaur.md in 02_agent_skills/)
 4. Could develop and achieve simple goals, as well as conceptualize long term goals.
 5. Memory file appears wasteful in performing AS memory.
 6. Model displayed exploratative desire and causal action, but dove into fights headlong. Frequently died.


 3. Subagent SDK

 haiku 4.5
 Observations
 1. the harness appropriately created a subagent to run dummy. successful in spatial navigation and even inferred that I might want to buy something from the baker, returning wares and telling me how much of each I could purchase
 2. The second subagent was particularly unintelligent, having to explore around again, despite the memory file, and improperly utilizing scripts. It attempted to create a new user "Goodbyemoon"
 3. Dummy successfully made it to the swordmaster's guild. Smarty had to be interrupted as it was going the opposite direction and was making its own scripts excessively.
 4. Definitive advantage is being able to run these subagents in parallel.
 