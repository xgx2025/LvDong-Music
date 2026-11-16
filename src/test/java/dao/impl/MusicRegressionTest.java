package dao.impl;

import junit.framework.TestCase;
import pojo.Singer;
import pojo.Song;
import service.impl.SearchServiceImpl;
import utils.DatabaseUtil;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/** Uses an isolated in-memory database, never the developer's MySQL. */
public class MusicRegressionTest extends TestCase {
    protected void setUp() throws Exception {
        // Fail before pool initialization if an external runtime config could override H2.
        for (String key : new String[]{"APP_CONFIG_FILE", "DB_URL", "DB_DRIVER", "DB_USERNAME", "DB_PASSWORD"}) {
            assertTrue("Unset runtime override before isolated tests: " + key,
                    System.getenv(key) == null || System.getenv(key).isEmpty());
        }
        try (Connection conn = DatabaseUtil.getConnection(); Statement sql = conn.createStatement()) {
            assertTrue(conn.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
            sql.execute("DROP ALL OBJECTS");
            sql.execute("CREATE TABLE songs (id BIGINT PRIMARY KEY, name VARCHAR(50), duration VARCHAR(10), "
                    + "album_id BIGINT, album_name VARCHAR(50), cover_url VARCHAR(255), lyric_url VARCHAR(255), "
                    + "audio_url VARCHAR(255), favorite_count INT DEFAULT 0, state INT DEFAULT 0)");
            sql.execute("CREATE TABLE song_singer (song_id BIGINT, singer_id BIGINT, singer_name VARCHAR(50), "
                    + "PRIMARY KEY(song_id, singer_id))");
            sql.execute("INSERT INTO songs (id,name,state,audio_url) VALUES "
                    + "(1,'match',1,'approved.mp3'),(2,'match',1,'second.mp3'),"
                    + "(3,'match',0,'pending.mp3'),(4,'match',2,'rejected.mp3')");
            sql.execute("INSERT INTO song_singer VALUES (1,11,'artist'),(1,12,'artist'),"
                    + "(2,11,'artist'),(3,11,'artist'),(4,11,'artist')");
        }
    }

    public void testAnonymousSearchAndPublicationBoundary() throws Exception {
        List<Song> songs = new SearchServiceImpl().searchSongs("match", 1, 10, null).get();
        assertEquals(2, songs.size());
        assertEquals(2, songs.get(0).getSingers().size());
        MusicDaoImpl dao = new MusicDaoImpl();
        assertEquals(2, dao.countSongsByKeyword("match"));
        assertEquals(2, dao.countSongsByKeyword("artist"));
        assertEquals("approved.mp3", dao.findMusicPathById(1L));
        assertNull(dao.findMusicPathById(3L));
        assertNull(dao.findMusicPathById(4L));
        assertNull(dao.findSongBySingerAndSongName("artist", "missing"));
        try (Connection conn = DatabaseUtil.getConnection(); Statement sql = conn.createStatement()) {
            sql.execute("UPDATE songs SET state=0 WHERE state=1");
        }
        assertNull(dao.findSongBySingerAndSongName("artist", "match"));
    }

    public void testPaginationCountsSongsInsteadOfSingerRows() {
        MusicDaoImpl dao = new MusicDaoImpl();
        assertEquals(2, dao.searchSongsByKeyword("artist", 1, 2, null).size());
        assertEquals("1", dao.searchSongsByKeyword("match", 1, 1, null).get(0).getSongId());
        assertEquals("2", dao.searchSongsByKeyword("match", 2, 1, null).get(0).getSongId());
    }

    public void testAssociationDoesNotCommitServiceTransaction() throws Exception {
        Singer singer = new Singer();
        singer.setId("99");
        singer.setName("new artist");
        DatabaseUtil.beginTransaction();
        try {
            assertTrue(new SongSingerDaoImpl().addSongSinger(1L, List.of(singer)));
            DatabaseUtil.rollback();
        } finally {
            DatabaseUtil.rollback();
        }
        assertNull(DatabaseUtil.getCurrentConnection());
        assertEquals(2, new SongSingerDaoImpl().findSingersBySongId(1L).size());
    }

    public void testStandaloneBatchRollsBackOnDuplicate() {
        Singer singer = new Singer();
        singer.setId("99");
        singer.setName("new artist");
        try {
            new SongSingerDaoImpl().addSongSinger(1L, List.of(singer, singer));
            fail("duplicate batch should fail");
        } catch (RuntimeException expected) {
            assertEquals(2, new SongSingerDaoImpl().findSingersBySongId(1L).size());
        }
    }
}
