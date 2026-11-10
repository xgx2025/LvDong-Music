/**
 * 双Token认证系统核心（localStorage统一存储版）
 * 功能：认证管理 + 路由守卫 + 受保护API请求
 */

// ==================== 存储管理 ====================
// const USER_INFO_KEY = 'userInfo';
if (typeof window.USER_INFO_KEY === 'undefined') {
    const USER_INFO_KEY = 'userInfo';
    window.USER_INFO_KEY = USER_INFO_KEY;
}

// 获取用户信息（包含Token）
function getUserInfo() {
    const info = localStorage.getItem(USER_INFO_KEY);
    return info ? JSON.parse(info) : null;
}

// 获取access token
function getAccessToken() {
    const info = getUserInfo();
    return info?.accessToken || null;
}

// 获取refresh token
function getRefreshToken() {
    const info = getUserInfo();
    return info?.refreshToken || null;
}

// 设置用户信息和Token
function setUserInfo(user, tokens) {
    localStorage.setItem(USER_INFO_KEY, JSON.stringify({
        id: user.id,
        username: user.username,
        email: user.email,
        avatar:user.avatar,
        gender:user.gender,
        bio:user.bio,
        registerTime:user.registerTime,
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken
    }));
}

// 清除所有用户信息
function clearUserInfo() {
    localStorage.removeItem(USER_INFO_KEY);
}

// 解析JWT payload（不验证签名，仅读取过期时间）
function parseJwtPayload(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        return JSON.parse(atob(base64));
    } catch (e) {
        return null;
    }
}

// 检查token是否过期（提前60秒视为过期，避免边界情况）
function isTokenExpired(token) {
    const payload = parseJwtPayload(token);
    if (!payload || !payload.exp) return true;
    return Date.now() >= (payload.exp * 1000 - 60000);
}

// 检查是否已认证
function isAuthenticated() {
    const info = getUserInfo();
    if (!info) return false;

    if (!info.accessToken || !info.refreshToken) return false;

    // 如果access token过期但refresh token有效，认为仍可认证
    if (isTokenExpired(info.accessToken)) {
        return !isTokenExpired(info.refreshToken);
    }

    return true;
}

// ==================== 跳转逻辑 ====================
function getBasePath() {
    const base = document.querySelector('base')?.href;
    return base ? new URL(base).pathname.replace(/\/$/, '') : '';
}

function redirectToLogin() {
    clearUserInfo();
    const currentPath = window.location.pathname.replace(getBasePath(), '') || '/';
    const loginUrl = `${getBasePath()}/login.html`;
    window.location.href = loginUrl;
}

// ==================== Token刷新 ====================
let isRefreshing = false;
let refreshSubscribers = [];

function subscribeTokenRefresh(callback) {
    refreshSubscribers.push(callback);
}

function onRefreshed(newToken) {
    refreshSubscribers.forEach(cb => cb(newToken));
    refreshSubscribers = [];
}

async function refreshToken() {
    // 防止重复刷新
    if (isRefreshing) {
        return new Promise(resolve => {
            subscribeTokenRefresh(resolve);
        });
    }

    try {
        isRefreshing = true;
        const userInfo = getUserInfo();
        const refreshToken = userInfo?.refreshToken;

        if (!refreshToken) {
            throw new Error('没有可用的刷新令牌');
        }

        const response = await axios.post('/userServlet?action=refreshToken', {
            refreshToken:refreshToken
        }, {
            timeout: 3000,
            _skipAuth: true
        });
        let newAccessToken=null;
        let newRefreshToken=null;
        // 如果响应头没有，尝试从响应体获取
        if (response.data?.tokens?.accessToken && response.data?.tokens?.refreshToken) {
            newAccessToken = response.data.tokens.accessToken;
            newRefreshToken = response.data.tokens.refreshToken;
        }

        if (!newAccessToken && !newRefreshToken) {
            throw new Error('无效的Token响应');
        }

        // 更新本地存储
        setUserInfo(userInfo, {
            accessToken: newAccessToken,
            refreshToken: newRefreshToken
        });

        // 通知所有等待的请求
        onRefreshed(newAccessToken);

        return newAccessToken;
    } catch (error) {
        console.error('刷新Token失败:', error);

        // 如果是401错误，表示refresh token无效
        if (error.response?.status === 401) {
            showToast('登录已过期，请重新登录');
        } else {
            showToast('网络错误，请稍后重试');
        }

        clearUserInfo();
        redirectToLogin();
        return null;
    } finally {
        isRefreshing = false;
    }
}

// ==================== 请求拦截器 ====================
axios.interceptors.request.use(config => {
    const token = getAccessToken();
    if (token && !config._skipAuth) {
        config.headers.Authorization = `Bearer ${token}`;
        // 添加额外头用于服务端识别
        config.headers['X-Access-Token'] = token;
    }
    return config;
});

// ==================== 响应拦截器 ====================
axios.interceptors.response.use(
    response => {
        // 检查响应头中是否有新的access token
        const newAccessToken = response.headers['authorization']?.replace('Bearer ', '');
        if (newAccessToken) {
            const userInfo = getUserInfo();
            if (userInfo) {
                setUserInfo(userInfo, {
                    accessToken: newAccessToken,
                    refreshToken: userInfo.refreshToken // 保留原refresh token
                });
            }
        }
        return response;
    },
    async error => {
        const originalRequest = error.config;

        // 处理401错误（未授权）
        if (error.response?.status === 401) {
            // 检查是否为刷新请求本身失败
            if (originalRequest.url.includes('/userServlet?action=refreshToken')) {
                clearUserInfo();
                redirectToLogin();
                return Promise.reject(error);
            }

            // 尝试刷新Token
            if (!originalRequest._retry) {
                originalRequest._retry = true;
                try {
                    await refreshToken();
                    // 使用新Token重试请求
                    const newToken = getAccessToken();
                    originalRequest.headers.Authorization = `Bearer ${newToken}`;
                    return axios(originalRequest);
                } catch (refreshError) {
                    return Promise.reject(refreshError);
                }
            }
        }

        return Promise.reject(error);
    }
);

// ==================== 路由守卫 ====================
async function checkAuthGuard() {
    const basePath = getBasePath();
    const fullPath = window.location.pathname;
    const currentPath = fullPath.startsWith(basePath)
        ? fullPath.substring(basePath.length)
        : fullPath;

    // 公开路径直接放行
    const publicPaths = [
        '/login.html',
        '/register.html',
        '/password-reset.html',
    ];

    if (publicPaths.includes(currentPath)) {
        return;
    }

    // 非公开路径：验证Token有效性
    try {
        const info = getUserInfo();
        if (!info || !info.refreshToken) {
            throw new Error('Token缺失');
        }

        // refresh token 过期，必须重新登录
        if (isTokenExpired(info.refreshToken)) {
            throw new Error('RefreshToken已过期');
        }

        // access token 过期，尝试刷新
        if (isTokenExpired(info.accessToken)) {
            const refreshSuccess = await refreshToken();
            if (!refreshSuccess) {
                throw new Error('Token刷新失败');
            }
        }
    } catch (error) {
        console.error('认证检查失败:', error);
        redirectToLogin();
        throw error;
    }
}

// ==================== 音乐人状态检查 ====================
const checkMusicianStatus = async (userId) => {
    try {
        const response = await axios.get(`/singerServlet?action=getStatus&singerId=${userId}`);
        return response.data.status;          // normal: 普通用户, pending: 审核中, approved: 已认证
    } catch (error) {
        console.error('获取音乐人状态失败:', error);
        throw error;
    }
};

const handleMusicianStatus = (status) => {
    switch(status) {
        case 'approved':
            return { canUpload: true, message: '' };
        case 'pending':
            return {
                canUpload: false,
                title: '您的音乐人申请正在审核中',
                description: '我们正在认真审核您的资料，通常需要1-3个工作日。审核通过后，您将可以上传音乐作品。',
                icon: 'hourglass-half',
                color: 'blue'
            };
        default:
            return {
                canUpload: false,
                title: '成为音乐人才能上传作品',
                description: '您需要申请成为认证音乐人后才能上传音乐作品。认证后，您的作品将被数百万用户发现！',
                icon: 'microphone',
                color: 'primary'
            };
    }
};

// ==================== 全局暴露 ====================
window.auth = {
    // 用户信息管理
    getUserInfo,
    setUserInfo,
    clearUserInfo,

    // Token管理
    getAccessToken,
    getRefreshToken,
    isAuthenticated,

    // 认证流程
    login: async (credentials) => {
        const response = await axios.post('/userServlet?action=login', credentials);
        if (response.data.success) {
            const {user, tokens} = response.data;
            setUserInfo(user, tokens);
        }
        console.log(response.data)
        return response;
    },

    refreshToken,

    logout: async () => {
        try {
            // 1. 发送登出请求到后端（携带当前 accessToken 用于服务器验证）
            const response = await axios.post('/userServlet?action=logout', null, {
                headers: { Authorization: `Bearer ${getAccessToken()}` },
                // 即使请求失败（如网络错误），也继续执行后续清理逻辑
                validateStatus: () => true  // 不拦截任何状态码，交由业务逻辑处理
            });

            // 2. 处理后端响应（可选，如提示登出成功/失败）
            if (response.data?.success) {
                showToast('登出成功', 'success');
            } else {
                showToast(response.data?.message || '登出请求处理失败', 'error');
            }
        } catch (error) {
            // 3. 捕获网络错误等异常（如断网时）
            console.error('登出请求异常:', error);
            showToast('网络异常，已强制登出', 'warning');
        } finally {
            // 4. 无论成功/失败，都清理本地数据并跳转登录页
            clearUserInfo();  // 清除 localStorage 中的用户信息和 token
            redirectToLogin();  // 跳转到登录页
        }
    },

    // 路由控制
    checkAuthGuard,
    redirectToLogin,

    // 受保护API请求
    protectedApiRequest: async (options, retryCount = 0) => {
        try {
            options.headers = options.headers || {};
            options.headers['Authorization'] = `Bearer ${getAccessToken()}`;

            const response = await axios(options);

            // 检查业务状态码（如401或403）
            if (response.data?.code === 401) {
                throw new Error('UNAUTHORIZED', { cause: response }); // 主动抛出错误
            }

            return response;
        } catch (error) {
            if (error.response?.status === 401 || error.message === 'UNAUTHORIZED') {
                if (retryCount >= 1) {
                    redirectToLogin();
                    throw new Error('REQUIRE_LOGIN');
                }

                const refreshed = await refreshToken();
                if (!refreshed) {
                    redirectToLogin();
                    throw new Error('REQUIRE_LOGIN');
                }
                return protectedApiRequest(options, retryCount + 1);
            }
            throw error;
        }
    },

    //音乐人验证
    checkMusicianStatus,
    handleMusicianStatus,
};

// ==================== 自动初始化 ====================
document.addEventListener('DOMContentLoaded', () => {
    auth.checkAuthGuard().catch(e => console.error('初始化认证检查失败:', e));
});

// ==================== 辅助函数 ====================
function showToast(message, type = 'info') {
    const toast = document.createElement('div');
    toast.className = `fixed inset-0 flex items-center justify-center p-4 z-50`;

    const toastContent = document.createElement('div');
    toastContent.className = `px-4 py-2 rounded-lg shadow-xl transition-all duration-300 transform translate-y-10 opacity-0 ${
        type === 'error' ? 'bg-red-500' :
            type === 'success' ? 'bg-green-500' :
                'bg-blue-500'
    } text-white text-center whitespace-nowrap inline-block`; // 关键修改

    toastContent.textContent = message;
    toast.appendChild(toastContent);
    document.body.appendChild(toast);

    // 显示动画
    setTimeout(() => {
        toastContent.classList.remove('translate-y-10', 'opacity-0');
    }, 10);

    // 自动消失
    setTimeout(() => {
        toastContent.classList.add('translate-y-10', 'opacity-0');
        setTimeout(() => toast.remove(), 300);
    }, 2000);
}

