package service;

import pojo.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface SearchService {

    SearchResult comprehensiveSearch(String keyword, int page, int pageSize,String userId);

    CompletableFuture<List<Song>> searchSongs(String keyword, int page, int pageSize,String userId);

    CompletableFuture<List<Singer>> searchSingers(String keyword, int page, int pageSize);

    CompletableFuture<List<Album>> searchAlbums(String keyword, int page, int pageSize);

    CompletableFuture<List<Playlist>> searchPlaylists(String keyword, int page, int pageSize);

}
