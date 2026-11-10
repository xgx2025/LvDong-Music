package dao.impl;

import dao.AlbumDao;
import pojo.Album;

import java.util.ArrayList;
import java.util.List;

public class AlbumDaoImpl implements AlbumDao {


    @Override
    public List<Album> searchAlbumsByKeyword(String keyword, int page, int pageSize) {
        // 实现类似searchSongsByKeyword
        List<Album> albums = new ArrayList<>();
        return  albums;
    }

    @Override
    public int countAlbumsByKeyword(String keyword) {
        return 0;
    }
}
