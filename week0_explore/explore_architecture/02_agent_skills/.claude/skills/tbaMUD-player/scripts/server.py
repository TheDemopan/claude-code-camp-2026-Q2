import socket
import os
import time
import threading

MUD_HOST = 'localhost'
MUD_PORT = 4000
USER = 'dummy'
PASSWORD = 'helloworld'
UNIX_SOCKET_PATH = '/tmp/tbaMUD.sock'

class MUDServer:
    def __init__(self):
        self.mud_socket = None
        self.is_running = True

    def connect_to_mud(self):
        print(f"Connecting to MUD at {MUD_HOST}:{MUD_PORT}...")
        self.mud_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            self.mud_socket.connect((MUD_HOST, MUD_PORT))
            self.mud_socket.settimeout(5.0)

            # Login process
            data = self.mud_socket.recv(4096).decode('utf-8', errors='ignore')
            print(f"Server Received: {data}", end="")
            if "Username:" in data:
                self.mud_socket.sendall((USER + "\n").encode())
                time.sleep(0.5)
                data = self.mud_socket.recv(4096).decode('utf-8', errors='ignore')
                print(f"Server Received: {data}", end="")
                if "Password:" in data:
                    self.mud_socket.sendall((PASSWORD + "\n").encode())
                    time.sleep(1.0)
                    data = self.mud_socket.recv(4096).decode('utf-8', errors='ignore')
                    print(f"Server Received: {data}", end="")
        except Exception as e:
            print(f"Login error: {e}")

    def run(self):
        # This is a placeholder for the actual server implementation
        pass

if __name__ == "__main__":
    server = MUDServer()
    server.connect_to_mud()
