import socket
import time

def connect():
    host = 'localhost'
    port = 4000
    username = 'dummy'
    password = 'helloworld'

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
        s.settimeout(10)
        
        # Function to read until a certain prompt or timeout
        def read_until(target):
            buffer = ""
            start_time = time.time()
            while time.time() - start_time < 5:
                try:
                    data = s.recv(1024).decode('utf-8', errors='ignore')
                    if not data:
                        break
                    buffer += data
                    print(data, end="")
                    if target in buffer:
                        return buffer
                except socket.timeout:
                    break
            return buffer

        # Initial banner/connection info
        initial_output = read_until("By what name do you wish to be an") # Partial match for "known?"
        print(f"\n--- Connection Output ---\n{initial_output}\n------------------------")

        # Handle the prompt "By what name do you wish to be known?"
        if "By what name do you wish to be known?" in initial_output:
            s.sendall((username + "\n").encode())
            time.sleep(1)
            # After name, it might ask for password or just log us in if dummy/helloworld is pre-auth'd 
            # But CLAUDE.md says we connect via these credentials. 
            # MUDs often prompt for password after name.
            password_prompt_output = read_until("Password:")
            if "Password:" in password_prompt_output:
                s.sendall((password + "\n").encode())
                time.sleep(1)

        # Let's see what the state is after login
        final_view = read_until(" ") # Read a bit more to see room description
        print(f"\n--- Post-Login View ---\n{final_view}\n-----------------------")
        
        s.close()

    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    connect()
