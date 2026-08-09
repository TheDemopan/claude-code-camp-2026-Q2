import socket
import time
import sys

def interact(command=None):
    host = 'localhost'
    port = 4000
    user = 'dummy'
    password = 'helloworld'

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))
        s.settimeout(5.0)

        def recv_until(target, timeout=5.0):
            buf = b""
            start_time = time.time()
            while time.time() - start_time < timeout:
                try:
                    data = s.recv(4096)
                    if not data:
                        break
                    buf += data
                    if target.encode() in data:
                        break
                except socket.timeout:
                    break
                except Exception:
                    break
            return buf.decode('utf-8', errors='ignore')

        def read_all_available():
            s.settimeout(1.0)
            buf = b""
            try:
                while True:
                    data = s.recv(4096)
                    if not data:
                        break
                    buf += data
            except socket.timeout:
                pass
            except Exception:
                pass
            return buf.decode('utf-8', errors='ignore')

        output = ""

        # Login process
        auth_output = recv_until("Username:", timeout=5.0)
        output += auth_output
        s.sendall((user + "\n").encode())

        pass_prompt = recv_until("Password:", timeout=5.0)
        output += pass_prompt
        s.sendall((password + "\n").encode())

        # Wait for server to process and send initial welcome/prompt
        time.sleep(1.0)
        welcome = read_all_available()
        output += welcome

        if command:
            s.sendall((command + "\n").encode())
            time.sleep(1.5) # Give the server time to process and respond
            cmd_response = read_all_available()
            output += cmd_response

        s.close()
        return output

    except Exception as e:
        error_msg = f"Error: {e}"
        print(error_msg)
        return error_msg

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else None
    # Print with end="" to avoid extra newline from print if we want raw output
    print(interact(cmd), end="")
