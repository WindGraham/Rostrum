#!/usr/bin/env python3
"""MJPEG 流服务器：把 v4l2loopback 画面转成浏览器可读的 MJPEG 流"""
import subprocess
import socketserver
from http.server import BaseHTTPRequestHandler

DEVICE = '/dev/video2'
PORT = 8081

class MJPEGHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass  # 静默日志

    def do_GET(self):
        if self.path == '/stream.mjpg':
            self.send_response(200)
            self.send_header('Content-type', 'multipart/x-mixed-replace; boundary=frame')
            self.send_header('Cache-Control', 'no-cache, no-store, must-revalidate')
            self.end_headers()

            cmd = [
                'ffmpeg', '-y', '-f', 'v4l2', '-input_format', 'yu12',
                '-i', DEVICE,
                '-vf', 'fps=15,scale=632:1368', '-f', 'mjpeg', '-q:v', '4',
                'pipe:1'
            ]
            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)

            buffer = b''
            while True:
                chunk = proc.stdout.read(4096)
                if not chunk:
                    break
                buffer += chunk
                while True:
                    soi = buffer.find(b'\xff\xd8')
                    if soi == -1:
                        break
                    eoi = buffer.find(b'\xff\xd9', soi)
                    if eoi == -1:
                        break
                    frame = buffer[soi:eoi+2]
                    buffer = buffer[eoi+2:]
                    try:
                        self.wfile.write(b'--frame\r\n')
                        self.wfile.write(b'Content-Type: image/jpeg\r\n')
                        self.wfile.write(f'Content-Length: {len(frame)}\r\n'.encode())
                        self.wfile.write(b'\r\n')
                        self.wfile.write(frame)
                        self.wfile.write(b'\r\n')
                    except (BrokenPipeError, ConnectionResetError):
                        proc.terminate()
                        return
        else:
            self.send_error(404)

if __name__ == '__main__':
    with socketserver.ThreadingTCPServer(('', PORT), MJPEGHandler) as httpd:
        print(f"MJPEG stream: http://localhost:{PORT}/stream.mjpg")
        httpd.serve_forever()
