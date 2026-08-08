import socket
import time

def run_mud_session():
    host = 'localhost'
    port = 4000
    username = 'dummy'
    password = 'helloworld'

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
        s.settimeout(5)

        def send_and_wait(cmd, timeout=3):
            if cmd is not None:
                s.sendall((cmd + "\n").encode())
            time.sleep(1)
            buffer = ""
            start_time = time.time()
            while time.time() - start_time < timeout:
                try:
                    data = s.recv(4096).decode('utf-8', errors='ignore')
                    if not data: break
                    buffer += data
                    # Basic way to stop reading if we see a prompt character
                    if ">" in data or "]" in data or "[Client]" in data:
                        break
                except socket.timeout:
                    break
            return buffer

        print("--- Starting MUD Login Sequence ---")
        
        # 1. Initial connection/Banner
        banner = send_and_wait(None, timeout=2)
        print(f"Banner:\n{banner}")

        # 2. Handle Name Prompt
        if "By what name do you wish to be known?" in banner or "Password:" in banner:
             s.sendall((username + "\n").encode())
             time.sleep(1)

        # 3. Handle Password Prompt
        password_prompt = send_and_wait(None, timeout=2)
        print(f"After Name:\n{password_prompt}")
        if "Password:" in password_prompt or "known?" in password_prompt: # If it skipped directly to password or we are still at name
             s.sendall((password + "\n").encode())
             time.sleep(1)

        # 4. Handle Return/Enter (to bypass 'PRESS RETURN')
        print("Sending Enter to bypass PRESS RETURN...")
        s.sendall(("\n").encode())
        time.sleep(1)

        # 5. Handle the Menu ('Make your choice:')
        menu_output = send_and_wait(None, timeout=2)
        print(f"Menu:\n{menu_output}")
        if "Make your choice:" in menu_output:
            print("Selecting option 1 (Enter the game)...")
            s.sendall(("1\n").encode())
            time.sleep(2)

        # 6. Capture post-login/room info
        post_login = send_and_wait(None, timeout=2)
        print(f"Post-Login State:\n{post_login}")

        # 7. Run 'look'
        print("Running 'look'...")
        look_output = send_and_wait("look", timeout=3)
        print(f"Look Output:\n{look_output}")

        s.close()
        return post_login, look_output

    except Exception as e:
        print(f"Error: {e}")
        return None, None

if __name__ == "__main__":
    post, look = run_mud_session()
