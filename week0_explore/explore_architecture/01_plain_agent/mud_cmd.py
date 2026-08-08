import socket
import sys
import time

def run_mud_command(command):
    host = "localhost"
    port = 4000
    username = "dummy"
    password = "helloworld"

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(15)
        s.connect((host, port))

        def recv_until(delim):
            buf = ""
            while True:
                try:
                    chunk = s.recv(4096).decode('utf-8', errors='ignore')
                    if not chunk:
                        break
                    buf += chunk
                    if delim in buf:
                        break
                except socket.timeout:
                    break
            return buf

        # 1. Wait for name prompt
        recv_until("By what name do you wish to be known?")
        print("Name prompt received.")

        # 2. Send name
        s.sendall((username + "\n").encode())
        time.sleep(0.5)

        # 3. Wait for Password: prompt
        recv_until("Password:")
        print("Password prompt received.")

        # 4. Send password
        s.sendall((password + "\n").encode())
        time.sleep(0.5)

        # 5. Wait for the PRESS RETURN prompt
        recv_until("*** PRESS RETURN:")
        print("Press return prompt received.")

        # 6. Send Enter (newline) to proceed through the welcome screen
        s.sendall(("\n").encode())
        time.sleep(1)

        # 7. Wait for the menu choice: prompt
        recv_until("Make your choice:")
        print("Menu choice prompt received.")

        # 8. Send '1' to enter the game
        s.sendall(("1\n").encode())
        time.sleep(1)

        # 9. Now that we are in, send the user command
        print(f"Sending command: {command}")
        s.sendall((command + "\n").encode())
        time.sleep(2)

        # 10. Capture the output
        output = ""
        try:
            while True:
                chunk = s.recv(4096).decode('utf-8', errors='ignore')
                if not chunk:
                    break
                output += chunk
        except socket.timeout:
            pass

        print("--- START MUD OUTPUT ---")
        print(output)
        print("--- END MUD OUTPUT ---")

        s.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "look"
    run_mud_command(cmd)
