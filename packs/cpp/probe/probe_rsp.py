"""Bounded local RSP handshake through adb forward to a same-UID LLDB server."""

import socket


def packet(command):
    payload = command.encode("ascii")
    return b"$" + payload + b"#" + f"{sum(payload) & 255:02x}".encode()


def receive(sock):
    data = bytearray()
    while len(data) < 65536:
        try:
            chunk = sock.recv(4096)
        except socket.timeout as failure:
            raise TimeoutError(f"RSP response timed out after {bytes(data[:128])!r}") from failure
        if not chunk:
            raise EOFError("LLDB server disconnected")
        data.extend(chunk)
        if b"#" in data and len(data) >= data.index(b"#") + 3:
            return bytes(data)
    raise ValueError("Oversized RSP response")


def main():
    with socket.create_connection(("127.0.0.1", 5045), timeout=5) as connection:
        connection.settimeout(5)
        for command in ("qSupported:multiprocess+", "?"):
            connection.sendall(packet(command))
            answer = receive(connection)
            print(command, answer[:512])
            connection.sendall(b"+")


if __name__ == "__main__":
    main()
