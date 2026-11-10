// 自定义m3u8清单加载器（pLoader） - 继承默认Loader
class CustomPlaylistLoader extends Hls.DefaultConfig.loader {
    constructor(config) {
        super(config);

        // 保存原始load方法引用
        const originalLoad = this.load.bind(this);

        // 重写load方法
        this.load = function(context, config, callbacks) {

            // 仅处理manifest类型（m3u8文件）
            if (context.type === 'manifest') {
                const songId = songIdInput.value.trim();
                // 构建目标URL
                const baseUrl = document.baseURI;
                context.url = `${baseUrl}hls?action=serveHls&songId=${songId}&fileName=index.m3u8`;
                console.log('重写m3u8请求URL:', context.url);
            }

            // 调用原始load方法继续处理请求
            originalLoad(context, config, callbacks);
        };
    }
}

// 自定义ts分片加载器（fLoader） - 继承默认Loader
// 自定义ts分片加载器（fLoader） - 通过URL匹配而非context.type
class CustomFragmentLoader extends Hls.DefaultConfig.loader {
    constructor(config) {
        super(config);
        const originalLoad = this.load.bind(this);

        this.load = function(context, config, callbacks) {
            console.log('CustomFragmentLoader context:', context); // 打印完整context对象

            // 通过URL后缀判断是否为TS文件请求（关键修改）
            const isTsRequest = context.url && context.url.endsWith('.ts');

            if (isTsRequest) {
                const songId = songIdInput.value.trim();
                const originalUrl = context.url;
                const tsFileName = originalUrl.split('/').pop();
                const baseUrl = document.baseURI;
                context.url = `${baseUrl}hls?action=serveHls&songId=${songId}&fileName=${tsFileName}`;
                console.log('TS路径重写成功:', context.url);
            }
            originalLoad(context, config, callbacks);
        };
    }
}

let hls = null;
const audioElement = document.getElementById('audioPlayer');
const playerContainer = document.getElementById('playerContainer');
const errorContainer = document.getElementById('errorContainer');
const errorMessage = document.getElementById('errorMessage');
const statusElement = document.getElementById('status');
const loadBtn = document.getElementById('loadBtn');
const songIdInput = document.getElementById('songId');
const logoutBtn = document.getElementById('logoutBtn');
const progressContainer = document.getElementById('progressContainer');
const progressBar = document.getElementById('progressBar');
const currentTimeElement = document.getElementById('currentTime');
const durationElement = document.getElementById('duration');

// 格式化时间为 MM:SS 格式
function formatTime(seconds) {
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = Math.floor(seconds % 60);
    return `${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`;
}

// 更新进度条和时间显示
function updateProgress() {
    if (audioElement.duration) {
        const progress = (audioElement.currentTime / audioElement.duration) * 100;
        progressBar.style.width = `${progress}%`;
        currentTimeElement.textContent = formatTime(audioElement.currentTime);
        durationElement.textContent = formatTime(audioElement.duration);
    }
}

// 跳转到进度条点击位置
function seekToPosition(event) {
    const rect = progressContainer.getBoundingClientRect();
    const clickPosition = event.clientX - rect.left;
    const percentage = clickPosition / rect.width;
    const seekTime = percentage * audioElement.duration;
    audioElement.currentTime = seekTime;
}

// 检查HLS浏览器兼容性
function checkHlsSupport() {
    if (Hls.isSupported()) {
        return true;
    } else if (audioElement.canPlayType('application/vnd.apple.mpegurl')) {
        // Safari原生支持HLS
        return true;
    }
    showError('您的浏览器不支持HLS流媒体播放');
    return false;
}

// 显示错误信息
function showError(message) {
    errorMessage.textContent = message;
    errorContainer.classList.remove('hidden');
    playerContainer.classList.add('hidden');
}

// 清除错误显示
function clearError() {
    errorContainer.classList.add('hidden');
}

// 更新状态信息
function updateStatus(message) {
    statusElement.textContent = message;
}

// 销毁现有的HLS实例
function destroyHls() {
    if (hls) {
        hls.destroy();
        hls = null;
    }
    audioElement.src = '';
}

// 加载HLS音乐
async function loadHlsMusic(songId) {
    if (!checkHlsSupport()) return;

    try {
        clearError();
        updateStatus('正在请求音乐资源...');
        destroyHls();

        // 1. 触发切片生成
        const response = await auth.protectedApiRequest({
            method: 'post',
            url: `/hls?action=processMp3&songId=${songId}`
        });

        if (!response.data?.success) {
            throw new Error(response.data?.message || '请求切片失败');
        }

        // 2. 轮询检查状态（带超时）
        let retry = 0;
        const maxRetry = 30; // 30秒超时
        let isReady = false;

        while (retry++ < maxRetry) {
            const status = await auth.protectedApiRequest({
                method: 'get',
                url: `/hls?action=checkStatus&songId=${songId}`
            });

            if (status.data?.ready) {
                console.log('准备好了！！！')
                isReady = true;
                break;
            }

            await new Promise(resolve => setTimeout(resolve, 1000));
        }

        if (!isReady) {
            throw new Error('音乐资源准备超时');
        }

        // 3. 获取M3U8播放列表URL
        updateStatus('获取播放列表中...');
        const m3u8Url = response.data.hlsUrl;
        if (!m3u8Url) {
            throw new Error('无效的播放列表URL');
        }
        await initPlayer(m3u8Url);
    } catch (error) {
        showError(error.message);
        destroyHls();
    }
}

// 4. 根据浏览器支持情况选择播放方式
function initPlayer(m3u8Url) {
    return new Promise((resolve, reject) => {
        if (Hls.isSupported()) {
            hls = new Hls({
                xhrSetup: (xhr) => {
                    const token = auth.getAccessToken();
                    if (token) {
                        xhr.setRequestHeader('Authorization', `Bearer ${token}`);
                    }
                    xhr.withCredentials = true;
                },
                // 使用继承方式的自定义Loader
                pLoader: CustomPlaylistLoader,
                fLoader: CustomFragmentLoader
            });

            hls.loadSource(m3u8Url);
            hls.attachMedia(audioElement);

            hls.on(Hls.Events.MANIFEST_PARSED, () => {
                playerContainer.classList.remove('hidden');
                audioElement.play().then(resolve).catch(reject);
            });


            // 错误处理
            hls.on(Hls.Events.ERROR, (event, data) => {
                console.error('HLS错误:', data);
                if (data.fatal) {
                    switch(data.type) {
                        case Hls.ErrorTypes.NETWORK_ERROR:
                            hls.startLoad(); // 网络错误重试
                            break;
                        case Hls.ErrorTypes.MEDIA_ERROR:
                            hls.recoverMediaError(); // 媒体错误恢复
                            break;
                        default:
                            reject(new Error(`HLS致命错误: ${data.details}`));
                            destroyHls();
                            break;
                    }
                }
            });
        } else if (audioElement.canPlayType('application/vnd.apple.mpegurl')) {
            // Safari原生支持逻辑（同样重写URL）
            const songId = songIdInput.value.trim();
            const baseUrl = document.baseURI;
            audioElement.src = `${baseUrl}hls?action=serveHls&songId=${songId}&fileName=index.m3u8`;
            audioElement.addEventListener('loadedmetadata', () => {
                playerContainer.classList.remove('hidden');
                audioElement.play().then(resolve).catch(reject);
            });
        } else {
            reject(new Error('您的浏览器不支持HLS播放'));
        }
    });
}

// 事件监听
loadBtn.addEventListener('click', async () => {
    const songId = songIdInput.value.trim();
    if (!songId) {
        showError('请输入歌曲ID');
        return;
    }
    await loadHlsMusic(songId);
});

logoutBtn.addEventListener('click', () => {
    auth.logout();
});

// 进度条相关事件
audioElement.addEventListener('timeupdate', updateProgress);
audioElement.addEventListener('loadedmetadata', updateProgress);
progressContainer.addEventListener('click', seekToPosition);

// 初始化检查
document.addEventListener('DOMContentLoaded', () => {
    auth.checkAuthGuard().catch(error => {
        console.error('认证检查失败:', error);
    });
});

