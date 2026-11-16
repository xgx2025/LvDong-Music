package dao.impl;

import junit.framework.TestCase;
import service.impl.FavoriteServiceImpl;
import utils.DatabaseUtil;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit -Dtest only; requires a freshly initialized, disposable local MySQL. */
public class FavoriteMySqlIntegrationTest extends TestCase {
    public void testConcurrentFavoriteAndRollbackOnMySql() throws Exception {
        String url = System.getenv("DB_URL");
        assertNotNull("Use only the disposable MySQL test runner", url);
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/lvdong_ephemeral_regression.*"));
        try (Connection c = DatabaseUtil.getConnection(); Statement s = c.createStatement()) {
            // No DROP/TRUNCATE: a reused database fails here instead of touching existing tables.
            s.execute("CREATE TABLE songs (id BIGINT PRIMARY KEY, favorite_count INT DEFAULT 0) ENGINE=InnoDB");
            s.execute("CREATE TABLE song_likes (id BIGINT AUTO_INCREMENT PRIMARY KEY, song_id BIGINT NOT NULL, user_id BIGINT NOT NULL, created_at DATETIME) ENGINE=InnoDB");
            s.execute("INSERT INTO songs VALUES (1,0)");
        }
        FavoriteServiceImpl service = new FavoriteServiceImpl();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> work = new ArrayList<>();
            for (int i=0;i<24;i++) work.add(() -> service.addFavorite(1L,10L));
            for (Future<Boolean> f : pool.invokeAll(work)) assertTrue(f.get(20,TimeUnit.SECONDS));
            assertCounts(1);
            work.clear();
            for (int i=0;i<24;i++) work.add(() -> service.removeFavorite(1L,10L));
            for (Future<Boolean> f : pool.invokeAll(work)) assertTrue(f.get(20,TimeUnit.SECONDS));
            assertCounts(0);
            try (Connection c = DatabaseUtil.getConnection(); Statement s = c.createStatement()) {
                s.execute("ALTER TABLE songs ADD CONSTRAINT forbid_increment CHECK (favorite_count = 0)");
            }
            try { service.addFavorite(1L,10L); fail("counter constraint must roll back like insert"); }
            catch (RuntimeException expected) { assertCounts(0); }
        } finally { pool.shutdownNow(); }
    }
    private void assertCounts(int expected) throws Exception {
        try (Connection c = DatabaseUtil.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet r=s.executeQuery("SELECT COUNT(*) FROM song_likes")) { r.next(); assertEquals(expected,r.getInt(1)); }
            try (ResultSet r=s.executeQuery("SELECT favorite_count FROM songs WHERE id=1")) { r.next(); assertEquals(expected,r.getInt(1)); }
        }
    }
}
