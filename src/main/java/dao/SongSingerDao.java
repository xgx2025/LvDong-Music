package dao;

import pojo.Singer;

import java.util.List;

public interface SongSingerDao {
    boolean addSongSinger(Long songId,List<Singer> singer);
    List<Singer> findSingersBySongId(Long songId);
}
