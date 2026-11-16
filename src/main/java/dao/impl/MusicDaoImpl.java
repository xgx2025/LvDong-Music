package dao.impl;

import dao.MusicDao;
import dao.SongFavoriteDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.Comment;
import pojo.Playlist;
import pojo.Singer;
import pojo.Song;
import utils.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class MusicDaoImpl implements MusicDao {
    private static final Logger log = LoggerFactory.getLogger(MusicDaoImpl.class);

    SongFavoriteDao songFavoriteDao = new SongFavoriteDaoImpl();

    public String findMusicPathById(Long id) {
        String sql="SELECT audio_url FROM songs WHERE id = ? AND state = 1";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            log.debug("查询音乐路径中: id={}", id);
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("audio_url");
                }
            }
        } catch (SQLException e) {
            log.error("查询音乐路径失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询音乐路径失败", e);
        }
        return null;
    }

    public List<Song> searchSongsByKeyword(String keyword, int page, int pageSize,Long userId) {
        // 使用LinkedHashMap保证插入顺序，同时根据songId去重
        Map<String, Song> uniqueSongs = new LinkedHashMap<>();
        String sql = "SELECT s.id, s.name, s.duration, s.album_id, s.album_name, s.cover_url, s.lyric_url, s.favorite_count " +
                "FROM songs s WHERE s.state = 1 AND (s.name LIKE ? OR s.album_name LIKE ? " +
                "OR EXISTS (SELECT 1 FROM song_singer ss WHERE ss.song_id = s.id AND ss.singer_name LIKE ?)) " +
                "ORDER BY CASE WHEN s.name = ? THEN 1 WHEN s.name LIKE ? THEN 2 " +
                "WHEN s.album_name = ? THEN 3 " +
                "WHEN EXISTS (SELECT 1 FROM song_singer ss WHERE ss.song_id = s.id AND ss.singer_name = ?) THEN 4 " +
                "ELSE 5 END, s.id LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 设置查询参数
            stmt.setString(1, "%" + keyword + "%");      // 歌曲名模糊匹配
            stmt.setString(2, "%" + keyword + "%");      // 专辑名模糊匹配
            stmt.setString(3, "%" + keyword + "%");      // 歌手名模糊匹配
            stmt.setString(4, keyword);                     // 歌曲名精确匹配
            stmt.setString(5, keyword + "%");            // 歌曲名前缀匹配
            stmt.setString(6, keyword);
            stmt.setString(7, keyword);
            // EXISTS 保证一首歌曲一行，再按稳定顺序分页。
            stmt.setInt(8, pageSize);
            // OFFSET正确计算为(当前页-1)*页面大小
            stmt.setInt(9, (page - 1) * pageSize);

            try (ResultSet rs = stmt.executeQuery()) {
                // 循环添加数据，直到达到pageSize或没有更多数据
                while (rs.next() && uniqueSongs.size() < pageSize) {
                    String songId = rs.getString("id");
                    if (!uniqueSongs.containsKey(songId)) {
                        Song song = new Song();
                        song.setSongId(songId);
                        song.setSongName(rs.getString("name"));
                        song.setDuration(rs.getString("duration"));
                        song.setAlbumId(rs.getString("album_id"));
                        song.setAlbumName(rs.getString("album_name"));
                        song.setCoverUrl(rs.getString("cover_url"));
                        song.setLyricUrl(rs.getString("lyric_url"));
                        song.setFavoriteCount(rs.getInt("favorite_count"));
                        uniqueSongs.put(songId, song);
                    }
                }
                //为歌曲结果集添加用户收藏状态

                // 直接返回去重后的列表（SQL已分页）
                return new ArrayList<>(uniqueSongs.values());
            }
            } catch (SQLException e) {
                log.error("搜索歌曲失败: {}", e.getMessage(), e);
                throw new RuntimeException(e);
            }
    }



    @Override
    public List<Singer> getSingersBySongId(Long songId) {
        List<Singer> singers = new ArrayList<>();
        String sql = "SELECT singer_id, singer_name FROM song_singer WHERE song_id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, songId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Singer singer = new Singer();
                    singer.setId(rs.getString("singer_id"));
                    singer.setName(rs.getString("singer_name"));
                    singers.add(singer);
                }
            }

        } catch (SQLException e) {
            log.error("查询歌曲歌手信息失败: songId={}", songId, e);
        }
        return singers;
    }

    @Override
    public int countSongsByKeyword(String keyword) {
        String sql = "SELECT COUNT(*) FROM songs s WHERE s.state = 1 AND (s.name LIKE ? " +
                "OR s.album_name LIKE ? OR EXISTS (SELECT 1 FROM song_singer ss " +
                "WHERE ss.song_id = s.id AND ss.singer_name LIKE ?))";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + keyword + "%");  // 歌曲名模糊匹配
            stmt.setString(2, "%" + keyword + "%");  // 专辑名模糊匹配
            stmt.setString(3, "%" + keyword + "%");  // 歌手名模糊匹配

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("统计歌曲数量失败: {}", e.getMessage(), e);
        }

        return 0;
    }




    @Override
    public int countPlaylistsByKeyword(String keyword) {
        return 0;
    }

    @Override
    public boolean addSong(Song song) {
        // 新增id字段到SQL语句中
        String sql = "INSERT INTO songs (id, name, duration, audio_url, lyric_url, cover_url, album_id, album_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        Connection conn = null;
        try {
            conn = DatabaseUtil.getCurrentConnection() != null ? DatabaseUtil.getCurrentConnection() : DatabaseUtil.getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {

                // 设置id值（使用song对象中的songId）
                stmt.setLong(1, Long.parseLong(song.getSongId()));
                stmt.setString(2, song.getSongName());
                stmt.setString(3, song.getDuration());
                stmt.setString(4, song.getAudioUrl());

                // 处理歌词URL可能为null的情况
                if (song.getLyricUrl() != null) {
                    stmt.setString(5, song.getLyricUrl());
                } else {
                    stmt.setNull(5, java.sql.Types.VARCHAR);
                }

                stmt.setString(6, song.getCoverUrl());

                // 处理albumId可能为null的情况
                String albumId = song.getAlbumId();
                if (albumId != null && !albumId.trim().isEmpty()) {
                    stmt.setLong(7, Long.parseLong(albumId));
                } else {
                    stmt.setNull(7, java.sql.Types.BIGINT);
                }

                stmt.setString(8, song.getAlbumName());

                int affectedRows = stmt.executeUpdate();
                return affectedRows > 0;
            }
        } catch (SQLException e) {
            log.error("添加歌曲失败: {}", e.getMessage(), e);
            throw new RuntimeException("添加歌曲失败", e);
        } catch (NumberFormatException e) {
            log.error("ID格式错误: songId={} 或 albumId={}", song.getSongId(), song.getAlbumId(), e);
            throw new RuntimeException("ID格式错误", e);
        }
        finally {
            DatabaseUtil.releaseIfNotManaged(conn);
        }
    }

    @Override
    public List<Playlist> searchPlaylistsByKeyword(String keyword, int page, int pageSize) {
        // 实现类似searchSongsByKeyword
        List<Playlist> playlists = new ArrayList<>();
        return  playlists;
    }

    @Override
    public Song findSongBySingerAndSongName(String singerName, String songName) {
        String sql = "SELECT s.id, s.name, s.duration, s.album_id, s.album_name, " +
                "s.cover_url, s.lyric_url, s.audio_url, ss.singer_name " +
                "FROM songs s " +
                "JOIN song_singer ss ON s.id = ss.song_id " +
                "WHERE s.state = 1 AND ss.singer_name = ? AND s.name = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, singerName);
            stmt.setString(2, songName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Song song = new Song();
                    song.setSongId(rs.getString("id"));
                    song.setSongName(rs.getString("name"));
                    song.setDuration(rs.getString("duration"));
                    song.setAlbumId(rs.getString("album_id"));
                    song.setAlbumName(rs.getString("album_name"));
                    song.setCoverUrl(rs.getString("cover_url"));
                    song.setLyricUrl(rs.getString("lyric_url"));
                    song.setAudioUrl(rs.getString("audio_url"));

                    // 可以在这里设置歌手信息
                    List<Singer> singers = new ArrayList<>();
                    Singer singer = new Singer();
                    singer.setName(rs.getString("singer_name"));
                    singers.add(singer);
                    song.setSingers(singers);

                    return song;
                }
            }
        } catch (SQLException e) {
            log.error("根据歌手和歌曲名查询失败: singerName={}, songName={}", singerName, songName, e);
            throw new RuntimeException("根据歌手和歌曲名查询失败", e);
        }
        return null;
    }

    /**
     * 为歌曲设置当前用户的收藏状态
     */
    private void setSongFavoriteStatus(Song song, Long userId) throws SQLException {
        try {
            // 检查当前用户是否收藏了该歌曲
            boolean hasFavorite = songFavoriteDao.hasFavorite(Long.parseLong(song.getSongId()), userId);
            song.setUserHasFavorite(hasFavorite);
        } catch (NumberFormatException e) {
            song.setUserHasFavorite(false);
            log.error("歌曲ID格式错误: {}", e.getMessage());
        }
    }
}
