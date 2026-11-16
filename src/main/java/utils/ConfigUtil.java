package utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 配置工具类：优先从环境变量读取，fallback到application.properties
 */
public class ConfigUtil {

    private static final Properties properties = new Properties();

    static {
        try (InputStream is = ConfigUtil.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (IOException e) {
            throw new RuntimeException("无法加载 application.properties", e);
        }
        // 可选外部配置，部署时不必将私人配置放进 WAR。
        String externalFile = System.getenv("APP_CONFIG_FILE");
        if (externalFile != null && !externalFile.isBlank()) {
            try (InputStream is = Files.newInputStream(Path.of(externalFile))) {
                properties.load(is);
            } catch (IOException e) {
                throw new IllegalStateException("无法加载外部配置文件", e);
            }
        }
    }

    /**
     * 获取配置值（环境变量优先）
     * @param envKey 环境变量名
     * @param propKey properties文件中的key
     * @return 配置值
     */
    public static String get(String envKey, String propKey) {
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return properties.getProperty(propKey);
    }

    /**
     * 获取配置值（环境变量优先），带默认值
     */
    public static String get(String envKey, String propKey, String defaultValue) {
        String value = get(envKey, propKey);
        return value != null ? value : defaultValue;
    }

    public static int getInt(String envKey, String propKey, int defaultValue) {
        String value = get(envKey, propKey);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }

    public static long getLong(String envKey, String propKey, long defaultValue) {
        String value = get(envKey, propKey);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
