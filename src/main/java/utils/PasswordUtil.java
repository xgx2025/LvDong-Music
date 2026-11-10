package utils;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码工具类：BCrypt哈希 + 旧MD5迁移支持
 */
public class PasswordUtil {

    private static final int BCRYPT_ROUNDS = 10;

    /**
     * 使用BCrypt对密码进行哈希
     */
    public static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    /**
     * 验证密码是否匹配（支持BCrypt和旧MD5哈希）
     * @param rawPassword 用户输入的明文密码
     * @param storedHash 数据库中存储的哈希值
     * @return 是否匹配
     */
    public static boolean verify(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        // BCrypt哈希以$2a$或$2b$开头
        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$")) {
            return BCrypt.checkpw(rawPassword, storedHash);
        }
        // 旧MD5哈希（32位十六进制字符串）
        if (storedHash.length() == 32 && storedHash.matches("[a-f0-9]{32}")) {
            return MD5EncryptUtil.md5(rawPassword).equals(storedHash);
        }
        return false;
    }

    /**
     * 判断哈希值是否为旧的MD5格式
     */
    public static boolean isMD5Hash(String hash) {
        return hash != null && hash.length() == 32 && hash.matches("[a-f0-9]{32}");
    }
}
