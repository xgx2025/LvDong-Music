"""Run against Vite with isolated API and generated audio; never connects to a real DB."""
import io
import json
import math
import os
from pathlib import Path
import struct
import wave
from playwright.sync_api import sync_playwright, expect

out = Path(__file__).resolve().parents[1] / 'test-results'
out.mkdir(exist_ok=True)
buffer = io.BytesIO()
with wave.open(buffer, 'wb') as audio:
    audio.setnchannels(1)
    audio.setsampwidth(2)
    audio.setframerate(8000)
    audio.writeframes(b''.join(struct.pack('<h', int(1200 * math.sin(i * 2 * math.pi * 220 / 8000))) for i in range(8000 * 45)))
data = buffer.getvalue()
song = {'songId':'9223372036854775807','songName':'隔离音频 · 连续播放验证','duration':'00:45','albumId':None,'albumName':'浏览器测试夹具','coverUrl':None,'lyricUrl':None,'favoriteCount':0,'userHasFavorite':False,'singers':[{'id':'1','name':'测试歌手','avatar':None}]}
payload = {'code':0,'message':'操作成功','data':{'songs':[song],'songCount':1,'singers':song['singers'],'singerCount':1,'albums':[],'playlists':[],'albumCount':0,'playlistCount':0}}
with sync_playwright() as p:
    executable = os.environ.get('PLAYWRIGHT_CHROMIUM_EXECUTABLE')
    browser = p.chromium.launch(headless=True, executable_path=executable, args=['--autoplay-policy=no-user-gesture-required'])
    page = browser.new_page(viewport={'width':1440,'height':1000}, reduced_motion='reduce')
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.route('**/api/v1/search?*', lambda route: route.fulfill(json=payload))
    page.route('**/isolated-audio.wav', lambda route: route.fulfill(body=data, content_type='audio/wav', headers={'Accept-Ranges':'bytes'}))
    page.goto('http://127.0.0.1:5173')
    page.wait_for_load_state('networkidle')
    expect(page.get_by_role('heading', name='发现好声音.')).to_be_visible()
    page.screenshot(path=str(out / 'desktop.png'), full_page=True)
    page.get_by_role('button', name='加入队列 隔离音频 · 连续播放验证', exact=True).click()
    page.evaluate("""async () => {
      const {usePlayerStore} = await import('/src/stores/player.ts'); const store = usePlayerStore();
      store.setResolver(async () => ({url:'/isolated-audio.wav'}));
      window.testAudio = store.engine.audio; window.testPlay = () => store.select(0);
      window.testAudioLoads = 0; store.engine.audio.addEventListener('loadstart', () => window.testAudioLoads++);
    }""")
    page.evaluate('window.testPlay()')
    page.wait_for_function('window.testAudio.currentTime > 0.4')
    before = page.evaluate('({time:window.testAudio.currentTime,loads:window.testAudioLoads})')
    page.locator('nav a[href="#/queue"]').click()
    expect(page.get_by_role('heading', name='播放队列 1')).to_be_visible()
    page.locator('nav a[href="#/settings"]').click()
    page.locator('nav a[href="#/"]').click()
    page.wait_for_function(f'window.testAudio.currentTime > {before["time"] + .4}')
    assert page.evaluate('window.testAudioLoads') == before['loads'], 'route changes reloaded audio'
    assert page.evaluate("async () => (await import('/src/stores/player.ts')).usePlayerStore().engine.audio === window.testAudio")
    page.get_by_role('button', name='暂停', exact=True).click()
    assert page.evaluate('window.testAudio.paused')
    page.get_by_role('button', name='播放', exact=True).click()
    page.wait_for_function('!window.testAudio.paused')
    slider = page.get_by_role('slider', name='播放进度')
    slider.focus(); slider.press('End')
    page.get_by_role('button', name='展开', exact=True).click()
    expect(page.locator('.now-playing')).to_be_visible()
    page.get_by_role('button', name='收起', exact=True).click()
    # Error and empty responses use real rendered retry controls.
    page.route('**/api/v1/search?*', lambda route: route.fulfill(status=500, json={'code':500,'message':'服务异常'}))
    page.get_by_role('searchbox').fill('失败'); page.get_by_role('button', name='搜索音乐').click()
    expect(page.get_by_role('alert')).to_contain_text('500')
    page.unroute('**/api/v1/search?*')
    page.route('**/api/v1/search?*', lambda route: route.fulfill(json=payload))
    page.get_by_role('button', name='重试搜索').click()
    expect(page.locator('.song-row')).to_have_count(1)
    page.route('**/api/v1/search?*', lambda route: route.fulfill(json={**payload,'data':{**payload['data'],'songs':[],'songCount':0}}))
    page.get_by_role('searchbox').fill('空结果'); page.get_by_role('button', name='搜索音乐').click()
    expect(page.get_by_role('heading', name='暂时没有找到歌曲')).to_be_visible()
    page.unroute('**/api/v1/search?*')
    page.route('**/api/v1/search?*', lambda route: route.fulfill(json=payload))
    page.get_by_role('searchbox').fill('音乐'); page.get_by_role('button', name='搜索音乐').click()
    expect(page.locator('.song-row')).to_have_count(1)
    page.set_viewport_size({'width':390,'height':844})
    page.get_by_role('searchbox').focus()
    page.get_by_role('searchbox').blur()
    page.evaluate('scrollTo(0, 0)')
    assert page.evaluate("matchMedia('(prefers-reduced-motion: reduce)').matches")
    assert page.evaluate("""() => {
      const indicator = document.createElement('span'); indicator.className = 'loading-disc';
      document.body.append(indicator); const animation = getComputedStyle(indicator).animationName;
      indicator.remove(); return animation === 'none';
    }""")
    page.screenshot(path=str(out / 'mobile.png'), full_page=True)
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'mobile horizontal overflow'
    page.locator('nav a[href="#/queue"]').click()
    expect(page.get_by_role('button', name='移除 隔离音频 · 连续播放验证')).to_be_visible()
    page.get_by_role('button', name='移除 隔离音频 · 连续播放验证').click()
    assert page.evaluate('window.testAudio.paused')
    assert not errors, errors
    print(json.dumps({'result':'passed','checks':['search fixture','queue','real WAV playback','Audio identity across 3 routes','no media reload','pause/resume','keyboard seek','expand/collapse','HTTP error/retry','empty state','390px overflow','remove current','reduced motion'],'audioLoadsBeforeRoute':before['loads'],'consoleErrors':errors}, ensure_ascii=False))
    browser.close()
