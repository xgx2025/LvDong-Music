package service.impl;

import dao.SongFavoriteDao;
import dao.impl.SongFavoriteDaoImpl;
import service.FavoriteService;
import utils.DatabaseUtil;

import java.sql.*;

public class FavoriteServiceImpl implements FavoriteService {
    SongFavoriteDao songFavoriteDao = new SongFavoriteDaoImpl();

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
