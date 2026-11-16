package dao.impl;

import junit.framework.TestCase;
import service.impl.FavoriteServiceImpl;
import utils.DatabaseUtil;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class FavoriteRegressionTest extends TestCase {
    protected void setUp() throws Exception {
        for (String key : new String[]{"APP_CONFIG_FILE", "DB_URL", "DB_DRIVER", "DB_USERNAME", "DB_PASSWORD"}) {
            assertTrue("Unset external configuration: " + key, System.getenv(key) == null || System.getenv(key).isEmpty());
        }
        try (Connection c = DatabaseUtil.getConnection(); Statement s = c.createStatement()) {
            assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
            s.execute("DROP ALL OBJECTS");
            s.execute("CREATE TABLE songs (id BIGINT PRIMARY KEY, favorite_count INT DEFAULT 0)");
            s.execute("CREATE TABLE song_likes (id BIGINT AUTO_INCREMENT PRIMARY KEY, song_id BIGINT, user_id BIGINT, created_at TIMESTAMP, UNIQUE(song_id,user_id))");
            s.execute("INSERT INTO songs VALUES (1,0)");
        }
    }
    public void testConcurrentIdempotentAddsAndRemovals() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        FavoriteServiceImpl service = new FavoriteServiceImpl();
        try {
            List<Callable<Boolean>> adds = new ArrayList<>();
            for (int i = 0; i < 24; i++) adds.add(() -> service.addFavorite(1L, 10L));
            for (Future<Boolean> f : pool.invokeAll(adds)) assertTrue(f.get(10, TimeUnit.SECONDS));
            assertCounts(1);
            List<Callable<Boolean>> removals = new ArrayList<>();
            for (int i = 0; i < 24; i++) removals.add(() -> service.removeFavorite(1L, 10L));
            for (Future<Boolean> f : pool.invokeAll(removals)) assertTrue(f.get(10, TimeUnit.SECONDS));
            assertCounts(0);
        } finally { pool.shutdownNow(); }
    }
    public void testCounterFailureRollsBackInsertedLike() throws Exception {
        try (Connection c = DatabaseUtil.getConnection(); Statement s = c.createStatement()) {
            s.execute("ALTER TABLE songs ADD CONSTRAINT forbid_increment CHECK (favorite_count = 0)");
        }
        try { new FavoriteServiceImpl().addFavorite(1L, 10L); fail("counter update should fail"); }
        catch (RuntimeException expected) { assertCounts(0); }
    }
    public void testBatchReadsDoNotCloseServiceTransaction() throws Exception {
        DatabaseUtil.beginTransaction();
        try {
            Connection managed = DatabaseUtil.getCurrentConnection();
            assertTrue(new SongFavoriteDaoImpl().addFavorite(1L, 10L));
            assertEquals(Boolean.TRUE, new SongFavoriteDaoImpl().batchHasFavorite(List.of(1L), 10L).get(1L));
            assertFalse(managed.isClosed());
            assertTrue(new SongFavoriteDaoImpl().batchHasFavorite(null, 10L).isEmpty());
        } finally { DatabaseUtil.rollback(); }
        assertCounts(0);
    }
    private void assertCounts(int expected) throws Exception {
        try (Connection c = DatabaseUtil.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM song_likes")) { r.next(); assertEquals(expected, r.getInt(1)); }
            try (ResultSet r = s.executeQuery("SELECT favorite_count FROM songs WHERE id=1")) { r.next(); assertEquals(expected, r.getInt(1)); }
        }
    }
}
