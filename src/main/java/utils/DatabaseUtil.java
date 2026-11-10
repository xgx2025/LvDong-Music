package utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class DatabaseUtil {
    private static final HikariDataSource dataSource;
    private static final ThreadLocal<Connection> threadLocal = new ThreadLocal<>();
    private static final Logger log = LoggerFactory.getLogger(DatabaseUtil.class);

    static {
        // 配置 HikariCP 连接池
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ConfigUtil.get("DB_URL", "db.url"));
        config.setUsername(ConfigUtil.get("DB_USERNAME", "db.username"));
        config.setPassword(ConfigUtil.get("DB_PASSWORD", "db.password"));
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        // 连接池配置（根据业务调整）
        config.setMaximumPoolSize(10);       // 最大连接数
        config.setMinimumIdle(5);            // 最小空闲连接
        config.setConnectionTimeout(30000);  // 连接超时时间（毫秒）
        config.setIdleTimeout(600000);       // 空闲连接超时时间
        config.setMaxLifetime(1800000);      // 连接最大生命周期

        // MySQL 特定配置（可选）
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        dataSource = new HikariDataSource(config);
    }


    // 获取自动提交的新连接（适合查询操作）
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection(); // 直接返回连接池的连接（默认 autoCommit=true）
    }

    // 获取当前线程绑定的连接（不自动创建）
    public static Connection getCurrentConnection() {
        return threadLocal.get();
    }

    // 开启事务并绑定连接
    public static void beginTransaction() throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.setAutoCommit(false);
        threadLocal.set(conn);
    }

    // 提交事务并释放连接
    public static void commit() throws SQLException {
        Connection conn = getCurrentConnection();
        if (conn != null) {
            conn.commit();
            closeAndRemove();
        }
    }

    // 回滚事务并释放连接
    public static void rollback() {
        Connection conn = getCurrentConnection();
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                throw new RuntimeException("回滚事务失败", e);
            } finally {
                closeAndRemove();
            }
        }
    }

    // 关闭连接并清理 ThreadLocal
    private static void closeAndRemove() {
        Connection conn = getCurrentConnection();
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    if (!conn.getAutoCommit()) {
                        conn.rollback(); // 确保未提交的事务回滚
                    }
                    conn.close();
                }
            }catch (SQLException e) {
                log.error("关闭数据库连接失败: {}", e.getMessage(), e);
            } finally {
                //一定要remove,否则会报错（因为Tomcat底层使用了线程池技术）
                threadLocal.remove();
            }
        }
    }

}