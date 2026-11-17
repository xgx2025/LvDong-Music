const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');

process.chdir(__dirname);
if (!fs.existsSync('.env')) {
  console.error('缺少本地配置，请先运行 npm run setup。');
  process.exit(1);
}
process.loadEnvFile('.env');
if (Buffer.byteLength(process.env.QQ_SESSION_SECRET || '') < 32) {
  console.error('QQ_SESSION_SECRET 至少需要32字节，请检查本地 .env。');
  process.exit(1);
}
const port = Number(process.env.QQ_API_PORT || '3200');
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('QQ_API_PORT 无效');
process.env.PORT = String(port);
process.env.NODE_ENV = 'production';
process.env.AUTO_OPEN_EXPLORER = 'false';
process.env.QQ_DISABLE_UPDATE_CHECK = 'true';
process.env.LOG_LEVEL ||= 'warn';
process.env.QQ_AUTH_STATE_PATH = path.resolve(process.env.QQ_AUTH_STATE_PATH || '.auth-state/device.json');
process.env.QQ_AUTH_SESSION_PATH = path.resolve(process.env.QQ_AUTH_SESSION_PATH || '.auth-state/sessions.enc');

const entry = require.resolve('@yakult-green-tea/qq-music-api');
const upstreamRequire = createRequire(entry);
const Koa = upstreamRequire('koa');
const originalListen = Koa.prototype.listen;
// Pinned upstream auto-starts via app.listen(port) without a host setting.
// Scope the host binding to that synchronous import, then restore Koa immediately.
Koa.prototype.listen = function (requestedPort, callback) {
  return originalListen.call(this, requestedPort, '127.0.0.1', callback);
};
let server;
try { server = require('@yakult-green-tea/qq-music-api').server; }
finally { Koa.prototype.listen = originalListen; }
if (!server) throw new Error('QQ 音乐服务未创建监听端口');
server.once('error', error => {
  console.error(error.code === 'EADDRINUSE' ? `本机 ${port} 端口已被占用。` : 'QQ 音乐服务启动失败。');
  process.exitCode = 1;
});
server.once('listening', () => {
  console.log(`QQ 音乐服务已启动：http://127.0.0.1:${port}`);
  console.log('设备与加密会话保存在 .auth-state，退出请按 Ctrl+C。');
});
function stop() { server.close(() => process.exit(0)); setTimeout(() => process.exit(0), 3000).unref(); }
process.once('SIGINT', stop);
process.once('SIGTERM', stop);
