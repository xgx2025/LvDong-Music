package dao.impl;

import dao.SongSingerDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.Singer;
import utils.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SongSingerDaoImpl implements SongSingerDao {
    private static final Logger log = LoggerFactory.getLogger(SongSingerDaoImpl.class);


        @Override
        public boolean addSongSinger(Long songId, List<Singer> singers) {
            String sql = "INSERT INTO song_singer (song_id, singer_id, singer_name) VALUES (?, ?, ?)";
            Connection conn = null;
            boolean ownsTransaction = false;
            try {
                conn = DatabaseUtil.getWriteConnection();
                ownsTransaction = DatabaseUtil.getCurrentConnection() == null;
                if (ownsTransaction) conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (Singer singer : singers) {
                        stmt.setLong(1, songId);
                        stmt.setLong(2, Long.parseLong(singer.getId()));
                        stmt.setString(3, singer.getName());
                        stmt.addBatch();
                    }
                    for (int rows : stmt.executeBatch()) {
                        if (rows == java.sql.Statement.EXECUTE_FAILED || rows == 0) {
                            throw new SQLException("歌曲歌手关联批量写入失败");
                        }
                    }
                }
                if (ownsTransaction) conn.commit();
                return true;
            } catch (Exception e) {
                if (ownsTransaction && conn != null) {
                    try { conn.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
                }
                throw new RuntimeException("添加歌曲歌手关联失败", e);
            } finally {
                DatabaseUtil.releaseIfNotManaged(conn);
            }
        }

        /**
         * 根据歌曲ID查询关联的歌手列表
         */
        @Override
        public List<Singer> findSingersBySongId(Long songId) {
            String sql = "SELECT singer_id, singer_name FROM song_singer WHERE song_id = ?";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                log.debug("查询歌曲关联的歌手列表: songId={}", songId);
                try {
                    stmt.setLong(1, songId);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }

                try (ResultSet rs = stmt.executeQuery()) {
                    List<Singer> singers = new ArrayList<>();
                    while (rs.next()) {
                        Singer singer = new Singer();
                        singer.setId(String.valueOf(rs.getLong("singer_id")));
                        singer.setName(rs.getString("singer_name"));
                        singers.add(singer);
                    }
                    return singers;
                }

            } catch (SQLException e) {
                log.error("查询歌曲关联的歌手失败: {}", e.getMessage(), e);
                throw new RuntimeException("查询歌曲关联的歌手失败", e);
            }
        }
}
