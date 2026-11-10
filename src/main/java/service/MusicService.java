package service;

import com.google.gson.JsonObject;
import pojo.Song;

public interface MusicService {
    /**
     * 通过音乐ID查询音乐资源路径
     * @param id
     * @return
     */
    String getMusicPath(Long id);

    boolean upload(Song song, JsonObject musicFile,JsonObject lyricFile,JsonObject coverFile);
}
