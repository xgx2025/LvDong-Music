package service.impl;

import annotation.ParamName;
import dao.SingerDao;
import dao.impl.SingerDaoImpl;
import pojo.Singer;
import service.SingerService;
import utils.ConfigUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

public class SingerServiceImpl implements SingerService {
    SingerDao singerDao = new SingerDaoImpl();

    @Override
    public boolean applyMusician(Singer singer,String avatarBase64, String idCardFrontBase64, String idCardBackBase64) {
        // 1. 参数校验
        if (avatarBase64 == null || singer == null || idCardFrontBase64 == null || idCardBackBase64 == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        // 2. 定义存储路径
        String tomcatPath = ConfigUtil.get("STORAGE_TOMCAT_PATH", "storage.tomcat.path");
        String projectPath = ConfigUtil.get("STORAGE_PROJECT_PATH", "storage.project.path");
        String basePath = tomcatPath;
        String frontDir = basePath + "idCardFront/";
        String backDir = basePath + "idCardBack/";
        String avatarDir = projectPath + "static/images/singerAvatars/";
        String avatarTempDir = tomcatPath + "static/images/singerAvatars/";

        // 3. 创建目录（如果不存在）
        createDirIfNotExists(avatarDir);
        createDirIfNotExists(frontDir);
        createDirIfNotExists(backDir);

        // 4. 解析图片类型并生成文件名
        String avatarFile = null;
        String avatarTempFile = null;
        String frontFile = null;
        String backFile = null;

        try {
            // 4.1 处理头像（本地）
            String avatarType = getImageTypeFromBase64(avatarBase64);
            singer.setAvatar(singer.getId()+"."+avatarType);
            avatarFile = avatarDir + singer.getId() + "." + avatarType;
            saveBase64Image(avatarBase64, avatarFile);

            // 4.2 处理头像（服务器运行时）
            avatarTempFile = avatarTempDir + singer.getId() + "." + avatarType;
            saveBase64Image(avatarBase64, avatarTempFile);

            // 4.3 处理身份证正面
            String frontType = getImageTypeFromBase64(idCardFrontBase64);
            frontFile = frontDir + singer.getId() + "." + frontType;
            saveBase64Image(idCardFrontBase64, frontFile);

            // 4.4 处理身份证反面
            String backType = getImageTypeFromBase64(idCardBackBase64);
            backFile = backDir + singer.getId() + "." + backType;
            saveBase64Image(idCardBackBase64, backFile);

            // 5. 保存歌手信息到数据库
            singerDao.addSinger(singer);

            return true;
        } catch (Exception e) {
            // 6. 失败时清理已保存的文件
            if (avatarFile!=null) new File(avatarFile).delete();
            if (avatarTempFile!=null) new File(avatarTempFile).delete();
            if (frontFile != null) new File(frontFile).delete();
            if (backFile != null) new File(backFile).delete();
            throw new RuntimeException(e.getMessage(),e);
        }
    }

    @Override
    public int getStatus(String singerId) {
        try {
            return singerDao.getStatus(Long.parseLong(singerId));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 创建目录（如果不存在）
     */
    private void createDirIfNotExists(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 保存Base64图片到文件
     */
    private void saveBase64Image(String base64Image, String filePath) throws IOException {
        String base64Data = base64Image.split(",")[1]; // 去除前缀
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        try (OutputStream stream = new FileOutputStream(filePath)) {
            stream.write(imageBytes);
        }
    }

    // 从Base64字符串中提取MIME类型
    public static String getImageTypeFromBase64(String base64) {
        String[] parts = base64.split(",");
        if (parts.length > 1) {
            String mimeType = parts[0].split(";")[0].split(":")[1];
            return mimeType.split("/")[1]; // 返回"png", "jpeg", "webp"等
        }
        return null; // 无法识别
    }
}
