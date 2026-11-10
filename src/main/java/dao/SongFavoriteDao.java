package dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface SongFavoriteDao {

    boolean addFavorite(Long songId,Long userId) throws SQLException;
    boolean removeFavorite(Long songId,Long userId) throws SQLException;

    boolean hasFavorite(Long songId,Long userId);

    boolean updateFavoriteCount(Long songId, int increment);

    Map<Long, Boolean> batchHasFavorite(List<Long> songIds, Long userId);
}
