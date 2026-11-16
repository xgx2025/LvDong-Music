package service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dao.UserDao;
import dao.impl.UserDaoImpl;
import pojo.User;
import service.UserService;
import utils.ConfigUtil;
import utils.PasswordUtil;
import utils.SnowFlakeUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UserServiceImpl implements UserService {
    @Override
    public User getProfile(Long id) {
        return userDao.findProfileById(id);
    }
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private UserDao userDao = new UserDaoImpl();
    private final String avatarDefault = "static/images/userAvatars/default.png";
    private final Path uploadPathTemp = Paths.get(ConfigUtil.get("STORAGE_TOMCAT_PATH", "storage.tomcat.path"));
    private final Path uploadPathReal  = Paths.get(ConfigUtil.get("STORAGE_PROJECT_PATH", "storage.project.path"));

    @Override
    public boolean registerUser(User user) {
        synchronized (user.getEmail().intern()) {
            Long id = SnowFlakeUtil.nextId();
            user.setId(id);
            user.setPassword(PasswordUtil.hash(user.getPassword()));
            user.setGender("未知");
            user.setBio("简介待解锁，故事正在加载中…");
            user.setAvatar(avatarDefault);
            return userDao.addUser(user);
        }
    }

    @Override
    public User login(String email, String password) {
        User user = userDao.findByEmail(email);
        if (user == null) {
            return null;
        }
        String storedHash = user.getPassword();
        if (!PasswordUtil.verify(password, storedHash)) {
            return null;
        }
        // MD5迁移：如果密码还是旧MD5格式，自动升级为BCrypt
        if (PasswordUtil.isMD5Hash(storedHash)) {
            userDao.updatePasswordByEmail(email, PasswordUtil.hash(password));
        }
        user.setPassword(null); // 不要把密码哈希传到前端
        return user;
    }

    @Override
    public boolean resetPassword(String email, String password) {
        return userDao.updatePasswordByEmail(email, PasswordUtil.hash(password));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userDao.existsByEmail(email);
    }

    @Override
    public Object updateUserProfile(User user) {
        log.debug("更新用户资料: userId={}, bio={}", user.getId(), user.getBio());
        if (user.getBio() == null||user.getBio().isEmpty()) {
            user.setBio("简介待解锁，故事正在加载中…");
        }

        String fileName = null;
        String imageUrl = null;
        boolean isAvatarUpdated = false;

        // 先处理头像更新（如果有新头像）
        if (user.getAvatar() != null && user.getAvatar().startsWith("data:image")) {
            try {
                String base64Data = user.getAvatar().split(",")[1];
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);

                String extractFileExtension = extractFileExtension(user.getAvatar());
                fileName = user.getId()+"_"+ System.currentTimeMillis()  + extractFileExtension;
                imageUrl = "static/images/userAvatars/" + fileName;
                Path filePathTemp = uploadPathTemp.resolve(imageUrl);   //Tomcat运行时，图片存储到Tomcat中才能被被访问，但是当Tomcat重新部署时，Tomcat又会从本地项目中直接加载。
                Path filePathReal = uploadPathReal.resolve(imageUrl);   //所以需要在本地也存储一份（项目目录中），用于下次重新部署。

                // 创建父目录（如果不存在）
                Files.createDirectories(filePathTemp.getParent());
                Files.createDirectories(filePathReal.getParent());
                Files.write(filePathTemp, imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Files.write(filePathReal, imageBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                user.setAvatar(imageUrl);
                isAvatarUpdated = true;
            } catch (IOException e) {
                log.error("保存用户头像失败: {}", e.getMessage(), e);
                // 返回错误信息，而不是 null
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "头像保存失败");
                return result;
            }
        }

        // 执行数据库更新
        try {
            // 检查用户名是否已存在（排除当前用户）
            if (user.getUsername() != null) {
                Long id = userDao.queryIdByUsername(user.getUsername());
                log.debug("查询用户名ID: id={}, userId={}", id, user.getId());
                if (id != null && !id.equals(user.getId())) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", false);
                    result.put("message", "用户名已存在");
                    return result;
                }
            }
            String oldAvatarUrl = userDao.queryAvatarByUserId(user.getId());
            boolean updated = userDao.updateUser(user);
            if (updated) {
                // 删除旧头像（如果更新了头像）
                if (isAvatarUpdated && oldAvatarUrl != null && !oldAvatarUrl.isEmpty() && !oldAvatarUrl.equals(avatarDefault)) {
                    //本地删除
                    Path oldFilePath = uploadPathReal.resolve(oldAvatarUrl);
                    if (Files.exists(oldFilePath)) {
                        Files.delete(oldFilePath);
                        log.info("成功删除旧头像: {}", oldAvatarUrl);
                    }
                    //运行时的服务删除
                    Path oldFilePathTemp = uploadPathTemp.resolve(oldAvatarUrl);
                    if (Files.exists(oldFilePathTemp)) {
                        Files.delete(oldFilePathTemp);
                        log.info("成功删除旧头像临时文件: {}", oldFilePathTemp);
                    }

                }

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "资料更新成功");
                if (isAvatarUpdated) {
                    result.put("avatarUrl", imageUrl);
                }
                return result;
            } else{
                // 如果更新失败且头像已保存，考虑删除已保存的头像
                if (isAvatarUpdated) {
                    deleteFileQuietly(uploadPathReal.resolve(imageUrl));
                    deleteFileQuietly(uploadPathTemp.resolve(imageUrl));
                }
                return Collections.singletonMap("success", false);
            }
        } catch (Exception e) {
            // 如果更新失败且头像已保存，考虑删除已保存的头像
            if (isAvatarUpdated) {
                deleteFileQuietly(uploadPathReal.resolve(imageUrl));
                deleteFileQuietly(uploadPathTemp.resolve(imageUrl));
            }
            log.error("更新用户信息失败: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "更新用户信息失败: " + e.getMessage());
            return result;
        }
    }

    /**
     * 从Base64图像数据中提取文件扩展名
     * @param base64Data Base64图像数据
     * @return 文件扩展名，如.jpg、.png等
     */
    private String extractFileExtension(String base64Data) {
        // 从data:image/jpeg;base64,xxx中提取jpeg
        String format = base64Data.substring(11, base64Data.indexOf(";"));
        return switch (format.toLowerCase()) {
            case "jpeg" -> ".jpg";
            case "png" -> ".png";
            case "gif" -> ".gif";
            case "bmp" -> ".bmp";
            case "webp" -> ".webp";
            default -> ".png"; // 默认使用.png作为扩展名
        };
    }

    /**
     * 安静地删除文件，不抛出异常
     * @param path 文件路径
     */
    private void deleteFileQuietly(Path path) {
        try {
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException e) {
            log.error("删除文件失败: {}", e.getMessage(), e);
        }
    }
}
