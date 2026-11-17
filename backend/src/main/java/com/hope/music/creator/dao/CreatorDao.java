package com.hope.music.creator.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Persistence for profiles, applications and uploaded song relationships. */
@Repository
public class CreatorDao {
    private final JdbcTemplate jdbc;
    public CreatorDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean lockUser(String id) { return !jdbc.query("SELECT id FROM users WHERE id=? FOR UPDATE", (rs,n)->rs.getString(1), id).isEmpty(); }
    public Integer status(String id) { return jdbc.query("SELECT status FROM singers WHERE id=?", (rs,n)->rs.getInt(1), id).stream().findFirst().orElse(null); }
    public void updateProfile(String id, String username, String gender, String bio, String avatar) {
        jdbc.update("UPDATE users SET username=?,gender=?,bio=?,avatar=COALESCE(?,avatar) WHERE id=?", username, gender, bio, avatar, id);
    }
    public void apply(String id, String name, String gender, String nationality, String bio, String avatar, String front, String back) {
        jdbc.update("INSERT INTO singers(id,name,avatar,intro,gender,nationality,status) VALUES(?,?,?,?,?,?,0)", id, name, avatar, bio, gender, nationality);
        jdbc.update("INSERT INTO musician_materials(user_id,front_path,back_path) VALUES(?,?,?)", id, front, back);
    }
    public String approvedSinger(String id) { return jdbc.query("SELECT name FROM singers WHERE id=? AND status=1 FOR UPDATE", (rs,n)->rs.getString(1),id).stream().findFirst().orElse(null); }
    public void insertSong(String id, String name, String duration, String album, String audio, String cover, String lyric, Map<String,String> singers) {
        jdbc.update("INSERT INTO songs(id,name,duration,album_name,audio_url,cover_url,lyric_url,state,favorite_count) VALUES(?,?,?,?,?,?,?,0,0)",id,name,duration,album,audio,cover,lyric);
        singers.forEach((singerId,singerName)->jdbc.update("INSERT INTO song_singer(song_id,singer_id,singer_name) VALUES(?,?,?)",id,singerId,singerName));
    }
}
