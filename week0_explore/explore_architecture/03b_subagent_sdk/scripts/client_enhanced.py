import socket
import time
import sys

def interact_with_menu(command=None):
    host = 'localhost'
    port = 4000
    user = 'dummy'
    password = 'helloworld'

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))

        output = ""

        # Read until "Username:"
        s.settimeout(2.0)
        buf = b""
        try:
            while True:
                chunk = s.recv(4096)
                if not chunk:
                    break
                buf += chunk
                if b"Username:" in chunk:
                    break
        except socket.timeout:
            pass
        output += buf.decode('utf-8', errors='ignore')

        # Send username
        s.sendall((user + "\n").encode())
        time.sleep(0.3)

        # Read until "Password:"
        s.settimeout(2.0)
        buf = b""
        try:
            while True:
                chunk = s.recv(4096)
                if not chunk:
                    break
                buf += chunk
                if b"Password:" in chunk:
                    break
        except socket.timeout:
            pass
        output += buf.decode('utf-8', errors='ignore')

        # Send password
        s.sendall((password + "\n").encode())
        time.sleep(0.5)

        # Read until "PRESS RETURN:" or similar
        s.settimeout(2.0)
        buf = b""
        try:
            while True:
                chunk = s.recv(4096)
                if not chunk:
                    break
                buf += chunk
                if b"PRESS RETURN" in chunk or b"Make your choice" in chunk:
                    break
        except socket.timeout:
            pass
        output += buf.decode('utf-8', errors='ignore')

        # If we see character confirmation prompt, answer yes
        if "Did I get that right" in output or "Y/N" in output:
            s.sendall(b"yes\n")
            time.sleep(0.5)
            s.settimeout(2.0)
            buf = b""
            try:
                while True:
                    chunk = s.recv(4096)
                    if not chunk:
                        break
                    buf += chunk
            except socket.timeout:
                pass
            output += buf.decode('utf-8', errors='ignore')

        # If we see "PRESS RETURN", send a newline
        if "PRESS RETURN" in output:
            s.sendall(b"\n")
            time.sleep(0.5)
            s.settimeout(2.0)
            buf = b""
            try:
                while True:
                    chunk = s.recv(4096)
                    if not chunk:
                        break
                    buf += chunk
            except socket.timeout:
                pass
            output += buf.decode('utf-8', errors='ignore')

        # Now we should be at the menu - send "1" to enter game
        if "Make your choice:" in output:
            s.sendall(b"1\n")
            time.sleep(2.0)
            s.settimeout(2.0)
            buf = b""
            try:
                while True:
                    chunk = s.recv(4096)
                    if not chunk:
                        break
                    buf += chunk
            except socket.timeout:
                pass
            output += buf.decode('utf-8', errors='ignore')

        # Send the actual game command
        if command:
            s.sendall((command + "\n").encode())
            time.sleep(1.5)
            s.settimeout(2.0)
            buf = b""
            try:
                while True:
                    chunk = s.recv(4096)
                    if not chunk:
                        break
                    buf += chunk
            except socket.timeout:
                pass
            output += buf.decode('utf-8', errors='ignore')

        s.close()
        return output

    except Exception as e:
        return f"Error: {e}"

if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else None
    print(interact_with_menu(cmd), end="")
