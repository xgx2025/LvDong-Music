package dao;

import pojo.Album;
import pojo.Playlist;
import pojo.Singer;

import java.util.List;

public interface AlbumDao {

    List<Album> searchAlbumsByKeyword(String keyword, int page, int pageSize);

    int countAlbumsByKeyword(String keyword);
}
