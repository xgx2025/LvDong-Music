package com.hope.music.search.dao;

import com.hope.music.search.domain.vo.SearchVO;
import com.hope.music.search.domain.vo.SearchVO.SingerVO;
import com.hope.music.search.domain.vo.SearchVO.SongVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** JDBC search adapter for the existing schema, without importing legacy runtime utilities. */
@Repository
public class SearchDao {
    private static final String MATCH = " FROM songs s WHERE s.state=1 AND (s.name LIKE ? "
            + "OR s.album_name LIKE ? OR EXISTS (SELECT 1 FROM song_singer ss "
            + "WHERE ss.song_id=s.id AND ss.singer_name LIKE ?))";
    private final JdbcTemplate jdbc;

    public SearchDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public SearchVO search(String keyword, int page, int pageSize) {
        String like = "%" + keyword + "%";
        long count = jdbc.queryForObject("SELECT COUNT(*)" + MATCH, Long.class, like, like, like);
        List<SongVO> songs = jdbc.query("SELECT s.id,s.name,s.duration,s.album_id,s.album_name,"
                        + "s.cover_url,s.lyric_url,s.favorite_count" + MATCH
                        + " ORDER BY CASE WHEN s.name=? THEN 1 WHEN s.name LIKE ? THEN 2 "
                        + "WHEN s.album_name=? THEN 3 WHEN EXISTS (SELECT 1 FROM song_singer ss "
                        + "WHERE ss.song_id=s.id AND ss.singer_name=?) THEN 4 ELSE 5 END,s.id LIMIT ? OFFSET ?",
                (rs, row) -> new SongVO(row + 1, rs.getString("id"), rs.getString("name"),
                        rs.getString("duration"), rs.getString("album_id"), rs.getString("album_name"),
                        rs.getString("cover_url"), rs.getString("lyric_url"), rs.getInt("favorite_count"),
                        false, new ArrayList<>()),
                like, like, like, keyword, keyword + "%", keyword, keyword, pageSize,
                ((long) page - 1) * pageSize);
        // One batch for all artists on the current page; no JOIN pagination or per-song queries.
        if (!songs.isEmpty()) {
            Map<String, List<SingerVO>> artists = new HashMap<>();
            songs.forEach(song -> artists.put(song.songId(), song.singers()));
            String placeholders = String.join(",", Collections.nCopies(songs.size(), "?"));
            jdbc.query("SELECT song_id,singer_id,singer_name FROM song_singer WHERE song_id IN ("
                            + placeholders + ") ORDER BY song_id,singer_id",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> artists.get(rs.getString("song_id"))
                            .add(new SingerVO(rs.getString("singer_id"), rs.getString("singer_name"), null)),
                    songs.stream().map(SongVO::songId).toArray());
        }
        long singerCount = jdbc.queryForObject("SELECT COUNT(*) FROM singers WHERE name LIKE ?", Long.class, like);
        List<SingerVO> singers = jdbc.query("SELECT id,name,avatar FROM singers WHERE name LIKE ? "
                        + "ORDER BY CASE WHEN name=? THEN 1 WHEN name LIKE ? THEN 2 ELSE 3 END,id LIMIT 8",
                (rs, row) -> new SingerVO(rs.getString("id"), rs.getString("name"), rs.getString("avatar")),
                like, keyword, keyword + "%");
        return new SearchVO(count, 0, singerCount, 0, songs, List.of(), singers, List.of());
    }
}
