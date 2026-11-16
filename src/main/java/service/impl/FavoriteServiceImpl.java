package service.impl;

import dao.SongFavoriteDao;
import dao.impl.SongFavoriteDaoImpl;
import service.FavoriteService;
import utils.DatabaseUtil;

import java.sql.*;

public class FavoriteServiceImpl implements FavoriteService {
    SongFavoriteDao songFavoriteDao = new SongFavoriteDaoImpl();

    @Override
    public pojo.PageResult<pojo.Song> getFavorites(Long userId, int page, int size) {
        java.util.List<pojo.Song> songs = songFavoriteDao.findFavorites(userId, page, size);
        dao.MusicDao musicDao = new dao.impl.MusicDaoImpl();
        for (pojo.Song song : songs) song.setSingers(musicDao.getSingersBySongId(Long.valueOf(song.getSongId())));
        return new pojo.PageResult<>(page, size, songFavoriteDao.countFavorites(userId), songs);
    }

    @Override
    public java.util.Map<Long, Boolean> getStates(Long userId, java.util.List<Long> songIds) {
        return songFavoriteDao.batchHasFavorite(songIds, userId);
    }

    @Override
    public boolean addFavorite(Long songId, Long userId) throws SQLException {
        DatabaseUtil.beginTransaction();
        try {
            boolean result = songFavoriteDao.addFavorite(songId, userId);
            DatabaseUtil.commit();
            return result;
        } catch (Exception e) {
            DatabaseUtil.rollback();
            throw e;
        }
    }

    @Override
    public boolean removeFavorite(Long songId, Long userId) throws SQLException {
        DatabaseUtil.beginTransaction();
        try {
            boolean result = songFavoriteDao.removeFavorite(songId, userId);
            DatabaseUtil.commit();
            return result;
        } catch (Exception e) {
            DatabaseUtil.rollback();
            throw e;
        }
    }
}
