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


    @Override
    public boolean addFavorite(Long songId, Long userId) throws SQLException {
        if (hasFavorite(songId, userId)) {
            return false;
        }
        String sql = "INSERT INTO song_likes (song_id, user_id,created_at) VALUES (?,?,?)";
        Connection conn = DatabaseUtil.getCurrentConnection() != null ? DatabaseUtil.getCurrentConnection() : DatabaseUtil.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, songId);
            stmt.setLong(2, userId);
            stmt.setTimestamp(3,new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
            updateFavoriteCount(songId, 1);
            return true;
        } catch (SQLException e) {
            log.error("添加收藏失败: songId={}, userId={}", songId, userId, e);
            return false;
        }
    }

    @Override
    public boolean removeFavorite(Long songId, Long userId) throws SQLException {
        // 先检查是否已经点赞
        if (!hasFavorite(songId, userId)) {
            return false; // 未点赞，无需取消
        }
        String sql = "DELETE FROM song_likes WHERE song_id = ? AND user_id = ?";
        Connection conn = DatabaseUtil.getCurrentConnection() != null ? DatabaseUtil.getCurrentConnection() : DatabaseUtil.getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 1. 删除点赞记录
            stmt.setLong(1, songId);
            stmt.setLong(2, userId);
            stmt.executeUpdate();
            // 2. 更新评论的点赞数
            updateFavoriteCount(songId, -1);
            return true;
        } catch (SQLException e) {
            log.error("取消收藏失败: songId={}, userId={}", songId, userId, e);
            return false;
        }
    }

    @Override
    public boolean hasFavorite(Long songId, Long userId) {
        String sql = "SELECT COUNT(*) FROM song_likes WHERE song_id = ? AND user_id = ?";
        try(Connection conn = DatabaseUtil.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)){
            stmt.setLong(1,songId);
            stmt.setLong(2,userId);
            try(ResultSet rs = stmt.executeQuery()){
                return rs.next()&& rs.getInt(1)>0;
            }
        }catch (Exception e){
            log.error("查询收藏状态失败: songId={}, userId={}", songId, userId, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean updateFavoriteCount(Long songId, int increment) {
        String sql = "UPDATE songs SET favorite_count= favorite_count+ ? WHERE id = ?";
        try(PreparedStatement stmt = DatabaseUtil.getCurrentConnection().prepareStatement(sql)){
            stmt.setInt(1,increment);
            stmt.setLong(2,songId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("更新收藏数量失败: songId={}", songId, e);
            throw new RuntimeException(e);
        }
        return false;
    }

    @Override
    public Map<Long, Boolean> batchHasFavorite(List<Long> songIds, Long userId) {
        // 结果集合，默认所有歌曲都未收藏
        Map<Long, Boolean> resultMap = new HashMap<>();
        for (Long songId : songIds) {
            resultMap.put(songId, false);
        }

        // 处理空列表情况
        if (songIds == null || songIds.isEmpty() || userId == null) {
            return resultMap;
        }

        // 构建SQL查询，使用IN语句匹配多个歌曲ID
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT song_id FROM song_likes WHERE user_id = ? AND song_id IN (");

        // 动态生成与歌曲ID数量匹配的占位符
        for (int i = 0; i < songIds.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            // 设置用户ID参数
            stmt.setLong(1, userId);

            // 设置所有歌曲ID参数
            for (int i = 0; i < songIds.size(); i++) {
                stmt.setLong(i + 2, songIds.get(i));
            }

            // 执行查询并处理结果
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long songId = rs.getLong("song_id");
                    resultMap.put(songId, true);
                }
            }

        } catch (SQLException e) {
            log.error("批量查询歌曲收藏状态失败: userId={}", userId, e);
        }

        return resultMap;
    }

    // 统一获取连接的方法，确保事务一致性
    private Connection getConnection() throws SQLException {
        Connection conn = DatabaseUtil.getCurrentConnection();
        return conn != null ? conn : DatabaseUtil.getConnection();
    }
}
