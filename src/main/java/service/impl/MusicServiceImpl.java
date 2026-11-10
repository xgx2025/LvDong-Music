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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            String musicType =getFileExtension(musicFile.get("type").getAsString());
            musicFilePath = musicDir + id + "." + musicType;
            saveBase64File(musicFile.get("data").getAsString(), musicFilePath);
            song.setAudioUrl(musicFilePath);

            if (existLyricFile) {
                song.setLyricUrl("static/lrc/"+id + "." + "lrc");
                lyricFilePath = lyricDir + id + "." + "lrc";
                saveBase64File(lyricFile.get("data").getAsString(), lyricFilePath);
                lyricTempFilePath = lyricTempDir + id + "." + "lrc";
                saveBase64File(lyricFile.get("data").getAsString(), lyricTempFilePath);

            }
            String coverType =getFileExtension(coverFile.get("type").getAsString());
            song.setCoverUrl("static/images/songCovers/"+id+"."+coverType);
            coverFilePath = coverDir + id + "." + coverType;
            saveBase64File(coverFile.get("data").getAsString(), coverFilePath);


            coverTempFilePath = coverTempDir + id + "." + coverType;
            saveBase64File(coverFile.get("data").getAsString(), coverTempFilePath);

            // 开启事务
            DatabaseUtil.beginTransaction();
            boolean flag = musicDao.addSong(song);
            if (flag){
                List<Singer> singers = song.getSingers();
                for (Singer singer : singers) {
                    singer.setName(singerDao.findSingerNameById(Long.valueOf(singer.getId())));
                }
                songSingerDao.addSongSinger(id,singers);
                // 提交事务
                DatabaseUtil.commit();
            }

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

    // 定义MIME类型与文件后缀的映射关系
    private String getFileExtension(String mimeType) {
        Map<String, String> mimeToExt = new HashMap<>();
        // 音频类型映射
        mimeToExt.put("audio/mpeg", "mp3");
        mimeToExt.put("audio/wav", "wav");
        mimeToExt.put("audio/ogg", "ogg");
        mimeToExt.put("audio/flac", "flac");
        // 歌词文件（通常是txt或lrc）
        mimeToExt.put("text/plain", "txt");
        mimeToExt.put("application/octet-stream", "lrc"); // 若歌词文件MIME类型为二进制流
        // 图片类型（封面图）
        mimeToExt.put("image/jpeg", "jpg");
        mimeToExt.put("image/png", "png");
        mimeToExt.put("image/gif", "gif");

        // 默认返回原类型（去除斜杠，避免路径错误）
        return mimeToExt.getOrDefault(mimeType, mimeType.replace("/", ""));
    }
}
