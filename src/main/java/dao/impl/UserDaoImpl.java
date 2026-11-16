package dao.impl;

import dao.UserDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.User;
import utils.DatabaseUtil;

import java.sql.*;
import java.time.LocalDate;

public class UserDaoImpl implements UserDao {
    private static final Logger log = LoggerFactory.getLogger(UserDaoImpl.class);


    @Override
    public Long queryIdByUsername(String username) {
        String sql = "SELECT id FROM users WHERE username = ?"; // 仅查询必要字段
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (SQLException e) {
            log.error("查询用户名失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询用户名失败", e);
        }
        return null; // 用户名不存在
    }

    @Override
    public User findById(Long id) {
        String sql = "SELECT username,avatar FROM users WHERE id = ?"; // 仅查询必要字段
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUsername(rs.getString("username"));
                    user.setAvatar(rs.getString("avatar"));
                    return user;
                }
            }
        } catch (SQLException e) {
            log.error("查询用户信息失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询用户信息失败", e);
        }
        return null; // 用户名不存在
    }


    @Override
    public String queryAvatarByUserId(Long id) {
        String sql = "SELECT avatar FROM users WHERE id = ?"; // 仅查询必要字段
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("avatar");

                }
            }
        } catch (SQLException e) {
            log.error("查询用户名失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询用户名失败", e);
        }
        return null; // 用户名不存在
    }

    @Override
    public User queryUserByUsername(String Username) {
        return null;
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("查询用户失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询用户失败", e);
        }
    }


    /**
     * 添加新用户
     * @param user
     * @return
     */
    @Override
    public boolean addUser(User user) {
        String sql = "INSERT INTO users(id,username,password,email,gender,state,bio,avatar,created_at) VALUES (?, ?, ?, ?,?,?,?,?,?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getPassword());
            stmt.setString(4, user.getEmail());
            stmt.setString(5, user.getGender());
            stmt.setInt(6, 0);  //0-普通用户，1-后台管理员
            stmt.setString(7,user.getBio());
            stmt.setString(8, user.getAvatar());
            stmt.setObject(9, LocalDate.now());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            // 检查是否是唯一约束冲突
            if (e instanceof SQLIntegrityConstraintViolationException) {
                // 提取错误信息，判断是否是用户名重复
                if (e.getMessage().contains("Duplicate entry") && e.getMessage().contains("username")) {
                    throw new RuntimeException("用户名已存在，请选择其他用户名", e);
                }
                if (e.getMessage().contains("Duplicate entry") && e.getMessage().contains("email")) {
                    throw new RuntimeException("该邮箱已注册", e);
                }
                // 其他完整性约束冲突
                throw new RuntimeException("数据库约束冲突，无法添加用户", e);
            }
            // 其他SQL异常
            throw new RuntimeException("数据库操作失败，请稍后再试", e);
        }
    }

    @Override
    public User findProfileById(Long id) {
        String sql = "SELECT id, username, email, avatar, gender, bio, created_at FROM users WHERE id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return null;
                User user = new User();
                user.setId(rs.getLong("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setAvatar(rs.getString("avatar"));
                user.setGender(rs.getString("gender"));
                user.setBio(rs.getString("bio"));
                user.setRegisterTime(rs.getString("created_at"));
                return user;
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询个人资料失败", e);
        }
    }

    @Override
    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setId(rs.getLong("id"));
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    user.setEmail(rs.getString("email"));
                    user.setAvatar(rs.getString("avatar"));
                    user.setGender(rs.getString("gender"));
                    user.setBio(rs.getString("bio"));
                    user.setRegisterTime(rs.getString("created_at"));
                    return user;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询用户失败", e);
        }
        return null;
    }

    @Override
    public boolean updateUser(User user) {
        // 检查头像是否为空，决定是否更新头像字段
        if (user.getAvatar() == null || user.getAvatar().isEmpty()) {
            // 头像为空时，不更新头像字段
            String sql = "UPDATE users SET username=?, gender=?, bio=? WHERE id=?";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, user.getGender());
                stmt.setString(3, user.getBio());
                stmt.setLong(4, user.getId());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                log.error("UserDao---更新用户信息失败: {}", e.getMessage(), e);
                throw new RuntimeException("更新用户信息失败", e);
            }
        } else {
            String sql = "UPDATE users SET username=?, gender=?, bio=?, avatar=? WHERE id=?";
            try (Connection conn = DatabaseUtil.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, user.getUsername());
                stmt.setString(2, user.getGender());
                stmt.setString(3, user.getBio());
                stmt.setString(4, user.getAvatar());
                stmt.setLong(5, user.getId());
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                log.error("UserDao---更新用户信息失败: {}", e.getMessage(), e);
                throw new RuntimeException("更新用户信息失败", e);
            }
        }
    }

    @Override
    public boolean updatePasswordByEmail(String email, String password) {
        String sql = "UPDATE users SET password=? WHERE email=?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1,password);
            stmt.setString(2,email);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("UserDao---更新用户密码失败: {}", e.getMessage(), e);
            throw new RuntimeException("重置密码失败", e);
        }
    }


}
