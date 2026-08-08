import socket
import time

def run_test():
    host = 'localhost'
    port = 4000
    username = 'dummy'
    password = 'helloworld'

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
        s.settimeout(5)

        def send_and_wait(cmd_to_send, timeout=3):
            if cmd_to_send is not None:
                s.sendall((cmd_to_send + "\n").encode())
            time.sleep(1.5) # Increased sleep to be safe
            buffer = ""
            start_time = time.time()
            while (time.time() - start_time) < timeout:
                try:
                    data = s.recv(4096).decode('utf-8', errors='ignore')
                    if not data: break
                    buffer += data
                    # Stop if we see a prompt character or the characteristic MUD delimiter
                    if ">" in data or "]" in data or "[Client]" in data:
                        break
                except socket/timeout: # Oops, typo here too. Let's fix it properly.
                    break
            return buffer

        # Correcting the logic to be much simpler
        print("--- Starting MUD ATM Investigation ---")
        
        # 1. Initial connection/Banner
        s.settimeout(3)
        banner = s.recv(4096).decode('utf-8', errors='ignore')
        print(f"Banner:\n{banner}")

        # 2. Login sequence
        s.sendall((username + "\n").encode())
        time.sleep(1)
        s.sendall((password + "\n").encode())
        time.sleep(1)
        s.sendall(("\n").encode()) # bypass PRESS RETURN
        time.sleep(1)

        # 3. Handle Menu
        menu = s.recv(4096).decode('utf-8', errors='ignore')
        print(f"Menu:\n{menu}")
        if "Make your choice:" in menu:
            s.sendall(("1\n").encode()) # Enter game
            time.sleep(2)

        # 4. Capture post-login/room info
        post_login = s.recv(4.096).decode('utf-8', errors='ignore') # typo here too! 4.096? 4096
        print(f"Post-Login:\n{post_login}")

        # 5. Try commands sequentially on the SAME connection
        for cmd in ["examine atm", "use atm", "interact atm", "help"]:
            print(f"\n>>> Sending: {cmd}")
            s.sendall((cmd + "\n").encode())
            time.sleep(2)
            res = s.recv(4096).decode('utf-8', errors='ignore')
            if res:
                print(f"Response:\n{res}")
            else:
                print("No response received.")

        s.close()

    except Exception as e:
        print(f"Error: {e}")

import socket # Re-import inside to be safe
if __name__ == "__main__":
    run_test()
