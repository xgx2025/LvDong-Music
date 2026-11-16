package service.impl;

import com.google.gson.JsonObject;
import dao.MusicDao;
import dao.SingerDao;
import dao.SongSingerDao;
import dao.impl.MusicDaoImpl;
import dao.impl.SingerDaoImpl;
import dao.impl.SongSingerDaoImpl;
import pojo.Singer;
import pojo.Song;
import service.MusicService;
import utils.ConfigUtil;
import utils.DatabaseUtil;
import utils.SnowFlakeUtil;
import utils.UploadValidator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.List;

public class MusicServiceImpl implements MusicService {
    MusicDao musicDao =  new MusicDaoImpl();
    SingerDao singerDao = new SingerDaoImpl();
    SongSingerDao songSingerDao = new SongSingerDaoImpl();

    @Override
    public String getMusicPath(Long id) {
        return musicDao.findMusicPathById(id);
    }

    @Override
    public boolean upload(Song song, JsonObject musicFile, JsonObject lyricFile, JsonObject coverFile) {
        if (musicFile == null || coverFile == null ) {
            throw new IllegalArgumentException("参数不能为空");
        }
        boolean existLyricFile = true;
        if (lyricFile==null) existLyricFile =false;

        // 2. 定义存储路径
        String tomcatPath = ConfigUtil.get("STORAGE_TOMCAT_PATH", "storage.tomcat.path");
        String projectPath = ConfigUtil.get("STORAGE_PROJECT_PATH", "storage.project.path");
        String musicDir = tomcatPath + "music-resources/";
        String lyricDir = projectPath + "static/lrc/";
        String lyricTempDir = tomcatPath + "static/lrc/";
        String coverDir = projectPath + "static/images/songCovers/";
        String coverTempDir = tomcatPath + "static/images/songCovers/";
        // 3. 创建目录（如果不存在）
        createDirIfNotExists(musicDir);
        if (existLyricFile) {
            createDirIfNotExists(lyricDir);
            createDirIfNotExists(lyricTempDir);
        }
        createDirIfNotExists(coverDir);
        createDirIfNotExists(coverTempDir);

        // 4. 解析图片类型并生成文件名
        String musicFilePath = null;
        String lyricFilePath = null;
        String lyricTempFilePath = null;
        String coverFilePath = null;
        String coverTempFilePath = null;

        //生成音乐ID
        Long id = SnowFlakeUtil.nextId();
        song.setSongId(String.valueOf(id));
        try {

            // 音频：先按 MIME 白名单确定后缀，再校验大小
            String musicData = requireData(musicFile, "音频文件");
            UploadValidator.checkBase64Size(musicData, UploadValidator.KIND_AUDIO, "音频文件");
            String musicType = UploadValidator.resolveExtension(
                    UploadValidator.KIND_AUDIO, stringField(musicFile, "type"));
            musicFilePath = musicDir + id + "." + musicType;
            saveBase64File(musicData, musicFilePath);
            song.setAudioUrl(musicFilePath);

            if (existLyricFile) {
                String lyricData = requireData(lyricFile, "歌词文件");
                UploadValidator.checkBase64Size(lyricData, UploadValidator.KIND_LYRIC, "歌词文件");
                // 歌词统一存为 .lrc，后缀由白名单决定，不信任客户端 MIME
                UploadValidator.resolveExtension(UploadValidator.KIND_LYRIC, stringField(lyricFile, "type"));
                song.setLyricUrl("static/lrc/" + id + "." + "lrc");
                lyricFilePath = lyricDir + id + "." + "lrc";
                saveBase64File(lyricData, lyricFilePath);
                lyricTempFilePath = lyricTempDir + id + "." + "lrc";
                saveBase64File(lyricData, lyricTempFilePath);
            }

            String coverData = requireData(coverFile, "封面图片");
            UploadValidator.checkBase64Size(coverData, UploadValidator.KIND_IMAGE, "封面图片");
            String coverType = UploadValidator.resolveExtension(
                    UploadValidator.KIND_IMAGE, stringField(coverFile, "type"));
            song.setCoverUrl("static/images/songCovers/" + id + "." + coverType);
            coverFilePath = coverDir + id + "." + coverType;
            saveBase64File(coverData, coverFilePath);

            coverTempFilePath = coverTempDir + id + "." + coverType;
            saveBase64File(coverData, coverTempFilePath);

            // 开启事务
            DatabaseUtil.beginTransaction();
            boolean flag = musicDao.addSong(song);
            if (!flag) {
                // 写入失败必须抛出，由下方 catch 回滚并清理文件，避免“假成功”
                throw new IllegalStateException("歌曲记录写入失败");
            }
            List<Singer> singers = song.getSingers();
            for (Singer singer : singers) {
                singer.setName(singerDao.findSingerNameById(Long.valueOf(singer.getId())));
            }
            songSingerDao.addSongSinger(id, singers);
            // 提交事务
            DatabaseUtil.commit();

            return true;
        } catch (Exception e) {
            //回滚事务
            DatabaseUtil.rollback();
            // 6. 失败时清理已保存的文件
            if (musicFilePath!=null) new File(musicFilePath).delete();
            if (lyricFilePath!=null && existLyricFile) new File(lyricFilePath).delete();
            if (lyricTempFilePath!= null && existLyricFile) new File(lyricTempFilePath).delete();
            if (coverFilePath != null) new File(coverFilePath).delete();
            if (coverTempFilePath != null) new File(coverTempFilePath).delete();
            // 参数类错误（格式不支持、超大小）原样抛出，让前端看到具体原因
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException(e.getMessage(),e);
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

    private void saveBase64File(String base64Image, String filePath) throws IOException {
        // 处理 Base64 字符串（兼容带前缀和纯数据两种格式）
        String[] base64Parts = base64Image.split(",");
        String base64Data;
        if (base64Parts.length >= 2) {
            // 有前缀（如 data:...;base64,），取逗号后的部分
            base64Data = base64Parts[1];
        } else {
            // 无前缀，直接使用整个字符串
            base64Data = base64Parts[0];
        }
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        try (OutputStream stream = new FileOutputStream(filePath)) {
            stream.write(imageBytes);
        }
    }

    /** 取出 JSON 中的 data 字段，缺失直接报错 */
    private String requireData(JsonObject file, String label) {
        if (file == null || !file.has("data") || file.get("data").isJsonNull()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return file.get("data").getAsString();
    }

    /** 安全读取字符串字段，缺失返回 null */
    private String stringField(JsonObject file, String name) {
        if (file == null || !file.has(name) || file.get(name).isJsonNull()) {
            return null;
        }
        return file.get(name).getAsString();
    }
}
