package utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Token黑名单：登出后的token在此标记为失效
 * 使用ConcurrentHashMap存储过期token，定时清理已过期的条目
 */
public class TokenBlacklist {

    private static final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    static {
        // 每小时清理一次已过期的token
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "token-blacklist-cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(TokenBlacklist::cleanup, 1, 1, TimeUnit.HOURS);
    }

    /**
     * 将token加入黑名单
     * @param token JWT token字符串
     * @param expireAtMillis token的过期时间（毫秒时间戳）
     */
    public static void add(String token, long expireAtMillis) {
        blacklist.put(token, expireAtMillis);
    }

    /**
     * 检查token是否在黑名单中
     */
    public static boolean contains(String token) {
        Long expireAt = blacklist.get(token);
        if (expireAt == null) {
            return false;
        }
        // 如果已过期，移除并返回false
        if (System.currentTimeMillis() > expireAt) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    /**
     * 清理已过期的条目
     */
    private static void cleanup() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
