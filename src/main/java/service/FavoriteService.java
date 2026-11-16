package service;

import java.sql.SQLException;

public interface FavoriteService {
    pojo.PageResult<pojo.Song> getFavorites(Long userId, int page, int size);
    java.util.Map<Long, Boolean> getStates(Long userId, java.util.List<Long> songIds);

    boolean addFavorite(Long songId,Long userId) throws SQLException;

    boolean removeFavorite(Long songId,Long userId) throws SQLException;

}
