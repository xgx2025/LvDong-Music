package com.hope.music.provider.dao;

import com.hope.music.provider.domain.vo.QqLibraryVO.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;

/** User-private playlist snapshots, replaced atomically only after a complete upstream read. */
@Repository
public class QqPlaylistDao {
    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();
    public QqPlaylistDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<ImportedPlaylist> list(String userId) {
        return jdbc.query("SELECT id,provider_playlist_id,name,cover_url,song_count,imported_at FROM qq_playlist WHERE user_id=? ORDER BY imported_at DESC,id",
                (rs, row) -> new ImportedPlaylist(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getLong(6)), userId);
    }
    public ImportedPlaylist find(String userId, String id) {
        return jdbc.query("SELECT id,provider_playlist_id,name,cover_url,song_count,imported_at FROM qq_playlist WHERE user_id=? AND id=?",
                (rs, row) -> new ImportedPlaylist(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getLong(6)), userId, id)
                .stream().findFirst().orElse(null);
    }
    public List<Song> songs(String userId, String id, int offset, int limit) {
        return jdbc.query("SELECT s.metadata FROM qq_playlist_song s JOIN qq_playlist p ON p.id=s.playlist_id WHERE p.user_id=? AND p.id=? ORDER BY s.position LIMIT ? OFFSET ?",
                (rs, row) -> json.readValue(rs.getString(1), Song.class), userId, id, limit, offset);
    }
    @Transactional
    public ImportedPlaylist replace(String userId, String accountId, Playlist playlist, List<Song> songs) {
        var existing = jdbc.query("SELECT id FROM qq_playlist WHERE user_id=? AND source_account_id=? AND provider_playlist_id=?",
                (rs, row) -> rs.getString(1), userId, accountId, playlist.id());
        String id = existing.isEmpty() ? UUID.randomUUID().toString() : existing.getFirst();
        long now = System.currentTimeMillis();
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO qq_playlist(id,user_id,source_account_id,provider_playlist_id,name,cover_url,song_count,imported_at) VALUES(?,?,?,?,?,?,?,?)",
                    id, userId, accountId, playlist.id(), playlist.name(), playlist.coverUrl(), songs.size(), now);
        } else {
            jdbc.update("UPDATE qq_playlist SET name=?,cover_url=?,song_count=?,imported_at=? WHERE id=? AND user_id=?",
                    playlist.name(), playlist.coverUrl(), songs.size(), now, id, userId);
            jdbc.update("DELETE FROM qq_playlist_song WHERE playlist_id=?", id);
        }
        jdbc.batchUpdate("INSERT INTO qq_playlist_song(playlist_id,position,song_mid,metadata) VALUES(?,?,?,?)", songs, 100,
                new org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<Song>() {
                    int position;
                    public void setValues(java.sql.PreparedStatement statement, Song song) throws java.sql.SQLException {
                        statement.setString(1, id); statement.setInt(2, position++);
                        statement.setString(3, song.providerSongId()); statement.setString(4, json.writeValueAsString(song));
                    }
                });
        return new ImportedPlaylist(id, playlist.id(), playlist.name(), playlist.coverUrl(), songs.size(), now);
    }
    public void delete(String userId, String id) { jdbc.update("DELETE FROM qq_playlist WHERE user_id=? AND id=?", userId, id); }
}
