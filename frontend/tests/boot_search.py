"""Starts packaged Boot with a fresh in-memory H2 and verifies Vite proxy + rendered search.
Requires --h2-jar; no existing database is used or modified.
"""
import argparse
import os
from pathlib import Path
import subprocess
import time
import urllib.request
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--h2-jar', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
out = root / 'frontend/test-results'
out.mkdir(exist_ok=True)
schema = out / 'browser-schema.sql'
schema.write_text('''
CREATE TABLE songs(id BIGINT PRIMARY KEY,name VARCHAR(50),duration VARCHAR(10),album_id BIGINT,album_name VARCHAR(50),cover_url VARCHAR(255),lyric_url VARCHAR(255),audio_url VARCHAR(255),favorite_count INT DEFAULT 0,state INT);
CREATE TABLE song_singer(song_id BIGINT,singer_id BIGINT,singer_name VARCHAR(50),PRIMARY KEY(song_id,singer_id));
CREATE TABLE singers(id BIGINT PRIMARY KEY,name VARCHAR(50),avatar VARCHAR(255));
INSERT INTO songs(id,name,duration,state) VALUES(9007199254740993,'Boot search fixture','00:45',1),(2,'Hidden fixture','00:45',0);
INSERT INTO singers VALUES(11,'Fixture artist',NULL);
INSERT INTO song_singer VALUES(9007199254740993,11,'Fixture artist');
''', encoding='utf-8')
jar = root / 'backend/target/lvdong-backend-0.1.0-SNAPSHOT.jar'
command = ['java', f'-Dloader.path={Path(args.h2_jar).resolve().as_posix()}', '-cp', str(jar),
           'org.springframework.boot.loader.launch.PropertiesLauncher', '--server.address=127.0.0.1', '--server.port=18081',
           '--spring.datasource.url=jdbc:h2:mem:vue_browser;MODE=MySQL;DB_CLOSE_DELAY=-1',
           '--spring.datasource.username=sa', '--spring.datasource.password=', '--spring.datasource.driver-class-name=org.h2.Driver',
           '--spring.sql.init.mode=always', f'--spring.sql.init.schema-locations={schema.as_uri()}']
with (out / 'boot-browser.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
    try:
        for _ in range(90):
            if process.poll() is not None:
                raise RuntimeError('Isolated Boot exited; see test-results/boot-browser.log')
            try:
                with urllib.request.urlopen('http://127.0.0.1:18081/api/v1/search', timeout=1) as response:
                    if response.status == 200:
                        break
            except OSError:
                time.sleep(.5)
        else:
            raise RuntimeError('Isolated Boot did not start')
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True, executable_path=os.environ.get('PLAYWRIGHT_CHROMIUM_EXECUTABLE'))
            page = browser.new_page(viewport={'width':1440,'height':1000})
            page.goto('http://127.0.0.1:5173')
            page.wait_for_load_state('networkidle')
            expect(page.locator('.song-row')).to_have_count(1)
            expect(page.locator('.song-row')).to_contain_text('Boot search fixture')
            page.get_by_role('searchbox').fill('Fixture artist')
            page.get_by_role('button', name='搜索音乐').click()
            expect(page.locator('.song-row')).to_have_count(1)
            with page.expect_response('**/api/v1/search?*') as query:
                page.get_by_role('searchbox').fill('Hidden fixture')
                page.get_by_role('button', name='搜索音乐').click()
            assert query.value.json()['data']['songCount'] == 0
            expect(page.get_by_role('heading', name='暂时没有找到歌曲')).to_be_visible()
            page.get_by_role('searchbox').fill('Boot search fixture')
            page.get_by_role('button', name='搜索音乐').click()
            expect(page.locator('.song-row')).to_have_count(1)
            page.get_by_role('button', name='加入队列 Boot search fixture', exact=True).click()
            page.locator('nav a[href="#/queue"]').click()
            expect(page.locator('.queue-list')).to_contain_text('Boot search fixture')
            browser.close()
        print('PASS: Vite -> packaged Boot -> isolated H2 -> rendered search, singer lookup, audit filtering, queue')
    finally:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
