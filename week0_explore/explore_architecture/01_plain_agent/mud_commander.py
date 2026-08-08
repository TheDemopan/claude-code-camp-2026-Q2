import socket
import time
import sys

def run_mud_commands(commands, host='localhost', port=4000, username='dummy', password='helloworld'):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
        s.settimeout(10)

        buffer = ""

        def read_until_prompt(target, timeout=5):
            nonlocal buffer
            start_time = time.time()
            while time.time() - start_time < timeout:
                try:
                    data = s.recv(4096).decode('utf-8', errors='ignore')
                    if not data:
                        break
                    buffer += data
                    print(data, end="") # Print live output
                    if target in buffer:
                        return True
                except socket/timeout:
                    break
            return False

        # Login sequence
        # 1. Wait for Name prompt
        # Note: The banner might come first. We look for the name prompt.
        read_until_prompt("By what name do you wish to be known?")
        s.sendall((username + "\n").encode())

        # 2. Wait for Password prompt
        if read_until_prompt("Password:"):
            s.sendall((password + "\n").encode())

        # 3. Wait for the game prompt (e.g., ">" or " ") to know we are logged in
        time.sleep(1) # Give it a moment to process login

        # Now execute the user commands
        for cmd in commands:
            print(f"\n--- Executing Command: {cmd} ---")
            s.sendall((cmd + "\n").encode())
            time.sleep(2) # Wait for response
            # We print what we receive during this window
            # Note: The read_until_prompt logic is tricky because it needs to stop
            # when the command's output is done. In MUDs, the prompt ">" is the key.
            read_until_prompt(">")

        s.close()

    except Exception as e:
        print(f"\nError: {e}")

if __name__ == "__main__":
    # Usage: python3 mud_commander.py cmd1 cmd2 ...
    if len(sys.argv) < 2:
        print("Usage: python3 mud_commander.py <command1> <command2> ...")
        sys.exit(1)

    cmds = sys.argv[1:]
    run_mud_commands(cmds)
