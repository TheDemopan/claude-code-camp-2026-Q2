import socket
import time
import sys

def run_test(cmd):
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
            time.sleep(1)
            buffer = ""
            start_time = time.time()
            while (time.time() - start_time) < timeout:
                try:
                    data = s.recv(4096).decode('utf-8', errors='ignore')
                    if not data: break
                    buffer += data
                    if ">" in data or "]" in data:
                        break
                except socket.timeout:
                    break
            return buffer

        # Login sequence
        s.sendall((username + "\n").encode())
        time.sleep(1)
        s.sendall((password + "\n").encode())
        time.sleep(1)
        s.sendall(("\n").encode()) # bypass PRESS RETURN
        time.sleep(1)

        # Handle Menu
        menu = send_and_wait(None, timeout=2)
        if "Make your choice:" in menu:
            s.sendall(("1\n").encode()) # Enter game
            time.sleep(2)

        print(f"--- Testing Command: '{cmd}' ---")
        result = send_and_wait(cmd, timeout=4)
        print(f"Result:\n{result}")

        s.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    target_cmd = sys.argv[1] if len(sys.argv) > 1 else "examine atm"
    run_test(target_cmd)
