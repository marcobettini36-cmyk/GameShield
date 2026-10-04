"""Controlled physical TCP peer for emulator FIN/half-close/concurrency regressions."""
import socketserver, threading
completed_uploads = 0
upload_lock = threading.Lock()

class Peer(socketserver.StreamRequestHandler):
    def handle(self):
        global completed_uploads
        self.request.settimeout(30)
        mode = self.rfile.readline()
        if mode == b'EOF\n':
            self.wfile.write(b'x' * 65536)
            self.wfile.flush()
            self.request.shutdown(1)
            while self.rfile.read(8192):
                pass
        elif mode == b'HALF\n':
            self.wfile.write(b'ready')
            self.wfile.flush()
            self.request.shutdown(1)
            data = self.rfile.read()
            if data != b'y' * 65536:
                raise RuntimeError('Half-close upload truncated')
            with upload_lock:
                completed_uploads += 1
        elif mode == b'COUNT\n':
            with upload_lock:
                self.wfile.write(str(completed_uploads).encode() + b'\n')

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

if __name__ == '__main__':
    with Server(('0.0.0.0', 18080), Peer) as server:
        server.serve_forever()
