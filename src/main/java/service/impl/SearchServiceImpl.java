package service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dao.AlbumDao;
import dao.MusicDao;
import dao.SingerDao;
import dao.impl.AlbumDaoImpl;
import dao.impl.MusicDaoImpl;
import dao.impl.SingerDaoImpl;
import pojo.*;
import service.SearchService;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SearchServiceImpl implements SearchService {
    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    MusicDao musicDao = new MusicDaoImpl();
    SingerDao singerDao = new SingerDaoImpl();
    AlbumDao albumDao = new AlbumDaoImpl();

    @Override
    public Song getSong(Long id) {
        Song song = musicDao.findPublishedSong(id);
        if (song != null) song.setSingers(musicDao.getSingersBySongId(id));
        return song;
    }


    @Override
    public SearchResult comprehensiveSearch(String keyword, int page, int pageSize,String userId) {
        // 并行查询各类型结果和数量
        CompletableFuture<List<Song>> songsFuture = searchSongs(keyword, page, pageSize,userId);
        CompletableFuture<Integer> songCountFuture = CompletableFuture.supplyAsync(
                () -> musicDao.countSongsByKeyword(keyword));

        CompletableFuture<List<Album>> albumsFuture = searchAlbums(keyword, 1, 12);
        CompletableFuture<Integer> albumCountFuture = CompletableFuture.supplyAsync(
                () -> albumDao.countAlbumsByKeyword(keyword));

        CompletableFuture<List<Singer>> singersFuture = searchSingers(keyword, 1, 8);
        CompletableFuture<Integer> artistCountFuture = CompletableFuture.supplyAsync(
                () -> singerDao.countSingersByKeyword(keyword));

        CompletableFuture<List<Playlist>> playlistsFuture = searchPlaylists(keyword, 1, 8);
        CompletableFuture<Integer> playlistCountFuture = CompletableFuture.supplyAsync(
                () -> musicDao.countPlaylistsByKeyword(keyword));
        // 等待所有查询完成
        CompletableFuture.allOf(
                songsFuture, songCountFuture,
                albumsFuture, albumCountFuture,
                singersFuture, artistCountFuture,
                playlistsFuture,playlistCountFuture
        ).join();

        List<Song> songs = songsFuture.join();
        // 设置排名
        for (int i = 0; i < songs.size(); i++) {
            songs.get(i).setRank(i + 1);
        }

        return new SearchResult(songCountFuture.join(),
                albumCountFuture.join(),
                artistCountFuture.join(),
                playlistCountFuture.join(),
                songsFuture.join(),
                albumsFuture.join(),
                singersFuture.join(),
                playlistsFuture.join() // 示例中未显示歌单
        );
    }


    @Override
    public CompletableFuture<List<Song>> searchSongs(String keyword, int page, int pageSize,String userId) {
        return CompletableFuture.supplyAsync(() -> {
            // 查询歌曲基本信息
            List<Song> songs = musicDao.searchSongsByKeyword(keyword, page, pageSize, userId == null || userId.isBlank() ? null : Long.valueOf(userId));
            log.debug("歌曲查询结果: {}", songs);
            // 为每首歌曲查询歌手信息
            songs.forEach(song -> {
                List<Singer> singers = musicDao.getSingersBySongId(Long.parseLong(song.getSongId()));
                song.setSingers(singers);
            });
            log.debug("歌曲及歌手信息: {}", songs);
            return songs;
        });
    }

    @Override
    public CompletableFuture<List<Album>> searchAlbums(String keyword, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() ->
                albumDao.searchAlbumsByKeyword(keyword, page, pageSize)
        );
    }

    @Override
    public CompletableFuture<List<Singer>> searchSingers(String keyword, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() ->
                singerDao.searchSingersByKeyword(keyword, page, pageSize)
        );
    }

    @Override
    public CompletableFuture<List<Playlist>> searchPlaylists(String keyword, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() ->
                musicDao.searchPlaylistsByKeyword(keyword, page, pageSize)
        );
    }

}



