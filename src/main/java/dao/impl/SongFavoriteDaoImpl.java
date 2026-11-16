package dao.impl;

import dao.SongFavoriteDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.DatabaseUtil;

import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SongFavoriteDaoImpl implements SongFavoriteDao {
    private static final Logger log = LoggerFactory.getLogger(SongFavoriteDaoImpl.class);


    private Connection requireTransaction() {
        Connection conn = DatabaseUtil.getCurrentConnection();
        if (conn == null) throw new IllegalStateException("收藏写入必须在Service事务中执行");
        return conn;
    }

    private void lockSong(Long songId) throws SQLException {
        try (PreparedStatement stmt = requireTransaction().prepareStatement("SELECT id FROM songs WHERE id = ? FOR UPDATE")) {
            stmt.setLong(1, songId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) throw new SQLException("歌曲不存在");
            }
        }
    }

    @Override
    public boolean addFavorite(Long songId, Long userId) throws SQLException {
        lockSong(songId);
        if (hasFavorite(songId, userId)) return true;
        try (PreparedStatement stmt = requireTransaction().prepareStatement(
                "INSERT INTO song_likes (song_id, user_id, created_at) VALUES (?, ?, ?)")) {
            stmt.setLong(1, songId);
            stmt.setLong(2, userId);
            stmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            if (stmt.executeUpdate() != 1) throw new SQLException("收藏记录写入失败");
        }
        return updateFavoriteCount(songId, 1);
    }

    @Override
    public boolean removeFavorite(Long songId, Long userId) throws SQLException {
        lockSong(songId);
        try (PreparedStatement stmt = requireTransaction().prepareStatement(
                "DELETE FROM song_likes WHERE song_id = ? AND user_id = ?")) {
            stmt.setLong(1, songId);
            stmt.setLong(2, userId);
            int changed = stmt.executeUpdate();
            if (changed > 0) updateFavoriteCount(songId, -changed);
            return true;
        }
    }

    @Override
    public boolean hasFavorite(Long songId, Long userId) {
        if (userId == null) return false;
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM song_likes WHERE song_id = ? AND user_id = ?")) {
                stmt.setLong(1, songId);
                stmt.setLong(2, userId);
                try (ResultSet rs = stmt.executeQuery()) { return rs.next() && rs.getInt(1) > 0; }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询收藏状态失败", e);
        } finally { DatabaseUtil.releaseIfNotManaged(conn); }
    }

    @Override
    public boolean updateFavoriteCount(Long songId, int increment) {
        try (PreparedStatement stmt = requireTransaction().prepareStatement(
                "UPDATE songs SET favorite_count = GREATEST(COALESCE(favorite_count, 0) + ?, 0) WHERE id = ?")) {
            stmt.setInt(1, increment);
            stmt.setLong(2, songId);
            if (stmt.executeUpdate() != 1) throw new SQLException("收藏计数更新失败");
            return true;
        } catch (SQLException e) { throw new RuntimeException("更新收藏数量失败", e); }
    }

    @Override
    public Map<Long, Boolean> batchHasFavorite(List<Long> songIds, Long userId) {
        Map<Long, Boolean> result = new HashMap<>();
        if (songIds == null || songIds.isEmpty()) return result;
        songIds.forEach(id -> result.put(id, false));
        if (userId == null) return result;
        String placeholders = String.join(",", java.util.Collections.nCopies(songIds.size(), "?"));
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement("SELECT song_id FROM song_likes WHERE user_id = ? AND song_id IN (" + placeholders + ")")) {
                stmt.setLong(1, userId);
                for (int i = 0; i < songIds.size(); i++) stmt.setLong(i + 2, songIds.get(i));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) result.put(rs.getLong("song_id"), true);
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("批量查询收藏状态失败", e);
        } finally { DatabaseUtil.releaseIfNotManaged(conn); }
    }

    // 统一获取连接的方法，确保事务一致性
    private Connection getConnection() throws SQLException {
        Connection conn = DatabaseUtil.getCurrentConnection();
        return conn != null ? conn : DatabaseUtil.getConnection();
    }
}
