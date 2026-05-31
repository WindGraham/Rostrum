#!/usr/bin/env python3
"""从 v4l2loopback 抓取帧，保存为 JPEG，供浏览器定时刷新显示"""
import subprocess
import os
import sys

DEVICE = '/dev/video2'
OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'phone_frame.jpg')

print(f'[FrameGrabber] Starting... device={DEVICE} output={OUTPUT}')

cmd = [
    'ffmpeg', '-y', '-f', 'v4l2', '-i', DEVICE,
    '-vf', 'fps=10,scale=632:1368', '-f', 'mjpeg', '-q:v', '4',
    'pipe:1'
]

proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
buffer = b''
frame_count = 0

while True:
    chunk = proc.stdout.read(8192)
    if not chunk:
        print('[FrameGrabber] ffmpeg ended')
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
        with open(OUTPUT, 'wb') as f:
            f.write(frame)
        frame_count += 1
        if frame_count % 50 == 0:
            print(f'[FrameGrabber] Saved {frame_count} frames')
