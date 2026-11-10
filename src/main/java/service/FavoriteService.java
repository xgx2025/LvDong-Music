package service;

import java.sql.SQLException;

public interface FavoriteService {

    boolean addFavorite(Long songId,Long userId) throws SQLException;

    boolean removeFavorite(Long songId,Long userId) throws SQLException;

}
