import socket
import time

def explore_inn():
    host = 'localhost'
    port = 4000
    user = 'Smarty'
    password = 'goodbyemoon'

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect((host, port))

        output = ""

        # Login sequence
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

        s.sendall((user + "\n").encode())
        time.sleep(0.3)

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

        s.sendall((password + "\n").encode())
        time.sleep(0.5)

        s.settimeout(2.0)
        buf = b""
        try:
            while True:
                chunk = s.recv(4096)
                if not chunk:
                    break
                buf += chunk
                if b"PRESS RETURN" in chunk or b"Make your choice" in chunk or b"(Y/N)?" in chunk or b"Yes or No" in chunk:
                    break
        except socket.timeout:
            pass
        output += buf.decode('utf-8', errors='ignore')

        if "(Y/N)?" in output or "Yes or No" in output:
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

        # Navigation to Inn
        commands = [
            "south", "north", "east",  # From Temple to Temple Square then try east
            "look",
            "south",  # Back to Market Square
            "north",  # Back to Temple Square
            "look",
            "east",   # Try to go east from Temple Square to Inn
            "look",
            "north",  # Try north at Inn
            "look",
            "south",  # Back
            "look",
            "west",   # Try west
            "look"
        ]

        for cmd in commands:
            s.sendall((cmd + "\n").encode())
            time.sleep(0.8)
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
            output += "\n>>> " + cmd + "\n" + buf.decode('utf-8', errors='ignore')

        s.close()
        return output

    except Exception as e:
        return f"Error: {e}"

if __name__ == "__main__":
    print(explore_inn())
