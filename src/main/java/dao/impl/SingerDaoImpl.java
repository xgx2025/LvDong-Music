package dao.impl;

import dao.SingerDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.Singer;
import utils.DatabaseUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SingerDaoImpl implements SingerDao {
    private static final Logger log = LoggerFactory.getLogger(SingerDaoImpl.class);

    public List<Singer> searchSingersByKeyword(String keyword, int page, int pageSize) {
        List<Singer> singers = new ArrayList<>();
        String sql = "SELECT sg.id, sg.name,sg.avatar " +
                "FROM singers sg " +
                "WHERE sg.name LIKE ? " +
                "ORDER BY CASE " +
                "  WHEN sg.name = ? THEN 1 " +         // 精确匹配最高优先级
                "  WHEN sg.name LIKE ? THEN 2 " +      // 前缀匹配次之
                "  ELSE 3 END " +                      // 其他匹配
                "LIMIT ? OFFSET ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 设置查询参数
            stmt.setString(1, "%" + keyword + "%");      // 歌手名模糊匹配
            stmt.setString(2, keyword);                  // 歌手名精确匹配
            stmt.setString(3, keyword + "%");            // 歌手名前缀匹配
            stmt.setInt(4, pageSize);
            stmt.setInt(5, (page - 1) * pageSize);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Singer singer = new Singer();
                    singer.setId(rs.getString("id"));
                    singer.setName(rs.getString("name"));
                    singer.setAvatar(rs.getString("avatar"));
                    singers.add(singer);
                }
                return singers;
            }
        } catch (SQLException e) {
            log.error("搜索歌手失败: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }


    @Override
    public int countSingersByKeyword(String keyword) {
        String sql = "SELECT COUNT(*) FROM singers s WHERE s.name LIKE ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + keyword + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            log.error("统计歌手数量失败: {}", e.getMessage(), e);
        }
        return 0;
    }


    @Override
    public void addSinger(Singer singer) {
        String sql = "INSERT INTO singers (id,name, avatar, intro, gender, nationality, status) VALUES (?,?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 设置参数
            stmt.setLong(1, Long.parseLong(singer.getId()));
            stmt.setString(2, singer.getName());
            stmt.setString(3, singer.getAvatar());
            stmt.setString(4, singer.getIntro());
            stmt.setString(5, singer.getGender());
            stmt.setString(6, singer.getNationality());
            stmt.setInt(7, singer.getStatus());
            // 执行插入操作
            int affectedRows = stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("SingerDao---添加歌手失败: {}", e.getMessage(), e);
            // 检查是否是唯一约束冲突
            if (e instanceof SQLIntegrityConstraintViolationException) {
                // 提取错误信息，判断是否是用户名重复
                if (e.getMessage().contains("Duplicate entry") && e.getMessage().contains("singers.PRIMARY")) {
                    throw new RuntimeException("该用户已申请音乐人，不要重复申请", e);
                }
            }
            // 其他SQL异常
            throw new RuntimeException("数据库操作失败，请稍后再试", e);
        }
    }

    @Override
    public int getStatus(Long singerId) {
        String sql = "SELECT status FROM singers WHERE id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 设置参数：查询指定ID的状态
            stmt.setLong(1, singerId);
            // 执行查询
            try (ResultSet rs = stmt.executeQuery()) {
                // 判断查询结果是否存在
                if (rs.next()) {
                    // 存在则返回状态值
                    return rs.getInt("status");
                } else {
                    // 不存在则抛出异常（明确提示ID不存在）
                    throw new RuntimeException("歌手ID不存在：" + singerId);
                }
            }
        } catch (SQLException e) {
            // 处理数据库操作异常
            log.error("SingerDao---获取状态失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询歌手状态失败，请稍后再试", e);
        }
    }

    @Override
    public String findSingerNameById(Long singerId) {
        String sql = "SELECT name FROM singers WHERE id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            // 设置参数：查询指定ID的状态
            stmt.setLong(1, singerId);
            // 执行查询
            try (ResultSet rs = stmt.executeQuery()) {
                // 判断查询结果是否存在
                if (rs.next()) {
                    // 存在则返回状态值
                    return rs.getString("name");
                } else {
                    // 不存在则抛出异常（明确提示ID不存在）
                    throw new RuntimeException("歌手ID不存在：" + singerId);
                }
            }
        } catch (SQLException e) {
            // 处理数据库操作异常
            log.error("SingerDao---获取状态失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询歌手姓名失败，请稍后再试", e);
        }
    }
}
