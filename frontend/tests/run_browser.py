"""Manage a direct Vite child process for reproducible isolated browser checks."""
import argparse
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--h2-jar', help='Run packaged Boot + in-memory H2 search instead of mocked API/audio')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
out = root / 'test-results'
out.mkdir(exist_ok=True)
ports = [5173, 18081] if args.h2_jar else [5173]
for port in ports:
    with socket.socket() as probe:
        if probe.connect_ex(('127.0.0.1', port)) == 0:
            raise SystemExit(f'Port {port} is already in use; close the existing test server first.')
env = os.environ.copy()
if args.h2_jar:
    env['API_PROXY_TARGET'] = 'http://127.0.0.1:18081'
with (out / 'vite.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen([shutil.which('node') or 'node', 'node_modules/vite/bin/vite.js', '--host', '127.0.0.1', '--port', '5173', '--strictPort'], cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(60):
            if process.poll() is not None:
                raise RuntimeError('Vite exited; see test-results/vite.log')
            try:
                urllib.request.urlopen('http://127.0.0.1:5173', timeout=1).close()
                break
            except OSError:
                time.sleep(.3)
        else:
            raise RuntimeError('Vite startup timed out')
        command = [sys.executable, 'tests/boot_search.py', '--h2-jar', args.h2_jar] if args.h2_jar else [sys.executable, 'tests/browser.py']
        subprocess.run(command, cwd=root, env=env, check=True)
    finally:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
