package dao;

import pojo.Playlist;
import pojo.Singer;
import pojo.Song;

import java.util.List;

public interface MusicDao {
    pojo.Song findPublishedSong(Long id);
    /**
     * 通过音乐ID查找音乐资源路径
     * @param id
     * @return
     */
    String findMusicPathById(Long id);


    /**
     * 通过关键字称查询歌曲
     * @param keyword
     * @param page
     * @param pageSize
     * @return
     */
    List<Song> searchSongsByKeyword(String keyword,int page,int pageSize,Long userId);


    List<Playlist> searchPlaylistsByKeyword(String keyword, int page, int pageSize);




    List<Singer> getSingersBySongId(Long songId);

    int countSongsByKeyword(String keyword);



    int countPlaylistsByKeyword(String keyword);


    boolean addSong(Song song);

    Song findSongBySingerAndSongName(String singerName, String songName);
}
