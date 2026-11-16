package com.hope.music.library.dao;

import com.hope.music.search.domain.vo.SearchVO.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** JDBC adapter preserving publication filtering and serialized favorite counter updates. */
@Repository
public class LibraryDao {
    private final JdbcTemplate jdbc;
    public LibraryDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<SongVO> songs(String condition,Object... args) {
        var songs=jdbc.query("SELECT s.id,s.name,s.duration,s.album_id,s.album_name,s.cover_url,s.lyric_url,s.favorite_count FROM songs s WHERE s.state=1 AND "+condition,
            (rs,n)->new SongVO(n+1,rs.getString("id"),rs.getString("name"),rs.getString("duration"),rs.getString("album_id"),rs.getString("album_name"),rs.getString("cover_url"),rs.getString("lyric_url"),rs.getInt("favorite_count"),false,new ArrayList<>()),args);
        for(var song:songs) song.singers().addAll(jdbc.query("SELECT singer_id,singer_name FROM song_singer WHERE song_id=? ORDER BY singer_id",
            (rs,n)->new SingerVO(rs.getString(1),rs.getString(2),null),song.songId()));
        return songs;
    }
    public long count(String uid) { return jdbc.queryForObject("SELECT COUNT(*) FROM songs s WHERE s.state=1 AND EXISTS(SELECT 1 FROM song_likes l WHERE l.song_id=s.id AND l.user_id=?)",Long.class,uid); }
    public Map<String,Boolean> states(String uid,List<String> ids) {
        Map<String,Boolean> result=new LinkedHashMap<>(); ids.forEach(id->result.put(id,false));
        List<Object> args=new ArrayList<>();args.add(uid);args.addAll(ids);
        jdbc.query("SELECT l.song_id FROM song_likes l JOIN songs s ON s.id=l.song_id WHERE s.state=1 AND l.user_id=? AND l.song_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",
            (org.springframework.jdbc.core.RowCallbackHandler)rs->result.put(rs.getString(1),true),args.toArray());return result;
    }
    public boolean lock(String id,boolean approvedOnly) { return !jdbc.query("SELECT id FROM songs WHERE id=?"+(approvedOnly?" AND state=1":"")+" FOR UPDATE",(rs,n)->rs.getString(1),id).isEmpty(); }
    public void set(String uid,String id,boolean liked) {
        if(liked) {
            if(jdbc.queryForObject("SELECT COUNT(*) FROM song_likes WHERE user_id=? AND song_id=?",Long.class,uid,id)>0) return;
            jdbc.update("INSERT INTO song_likes(song_id,user_id,created_at) VALUES(?,?,CURRENT_TIMESTAMP)",id,uid);
            jdbc.update("UPDATE songs SET favorite_count=COALESCE(favorite_count,0)+1 WHERE id=?",id);
        } else {
            int removed=jdbc.update("DELETE FROM song_likes WHERE song_id=? AND user_id=?",id,uid);
            if(removed>0) jdbc.update("UPDATE songs SET favorite_count=GREATEST(COALESCE(favorite_count,0)-?,0) WHERE id=?",removed,id);
        }
    }
}
