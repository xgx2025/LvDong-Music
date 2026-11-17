const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');

// Fill missing local settings without rotating keys or changing unrelated configuration.
function fill(file, values) {
  let source = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
  for (const [key, fallback] of Object.entries(values)) {
    const pattern = new RegExp(`^${key}=([^\\r\\n]*)`, 'm');
    const match = source.match(pattern);
    if (match && match[1].trim()) continue;
    const value = typeof fallback === 'function' ? fallback() : fallback;
    const entry = `${key}=${value}`;
    source = match ? source.replace(pattern, () => entry) : `${source}${source.endsWith('\n') || !source ? '' : '\n'}${entry}\n`;
  }
  fs.writeFileSync(file, source, { mode: 0o600 });
}

const backendEnv = path.resolve(__dirname, '../../backend/.env');
if (!fs.existsSync(backendEnv)) {
  console.error('请先按 backend/.env.example 配置本站数据库，再运行 npm run setup。');
  process.exitCode = 1;
} else {
  fill(path.join(__dirname, '.env'), {
    QQ_SESSION_SECRET: () => randomBytes(32).toString('base64'),
    QQ_API_PORT: '3200',
  });
  const settings = require('node:util').parseEnv(fs.readFileSync(path.join(__dirname, '.env'), 'utf8'));
  const port = process.env.QQ_API_PORT || settings.QQ_API_PORT;
  if (!/^\d+$/.test(port) || Number(port) < 1024 || Number(port) > 65535) throw new Error('QQ_API_PORT 无效');
  fill(backendEnv, {
    QQ_MUSIC_API_BASE: `http://127.0.0.1:${port}`,
    QQ_MUSIC_CONNECTION_KEY: () => randomBytes(32).toString('base64'),
  });
  console.log('本地 QQ 服务及 Boot 连接配置已准备，已有密钥保持不变。');
  console.log('启用前需执行尚未应用的 006_qq_music_connection.sql、007_qq_playlist.sql，然后运行 npm start。');
}
