package com.hope.music.library.service;

import com.hope.music.library.dao.LibraryDao;
import com.hope.music.search.domain.vo.SearchVO.SongVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Transaction owner for library reads and idempotent writes. */
@Service
public class LibraryService {
    private final LibraryDao dao;
    public LibraryService(LibraryDao dao) { this.dao=dao; }
    public static String id(String id) {
        try { if(id!=null && id.matches("[0-9]{1,19}") && Long.parseLong(id)>0) return id; }catch(NumberFormatException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"ID无效");
    }
    @Transactional(readOnly=true) public Object song(String id) {
        var list=dao.songs("s.id=?",id(id));if(list.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"歌曲不存在");
        return Map.of("success",true,"song",list.getFirst());
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Object favorites(String uid,int page,int size) {
        page=Math.max(1,page);size=size<=0?20:Math.min(100,size);
        var list=dao.songs("EXISTS(SELECT 1 FROM song_likes l WHERE l.song_id=s.id AND l.user_id=?) ORDER BY s.id DESC LIMIT ? OFFSET ?",uid,size,((long)page-1)*size);
        var liked=list.stream().map(s->new SongVO(s.rank(),s.songId(),s.songName(),s.duration(),s.albumId(),s.albumName(),s.coverUrl(),s.lyricUrl(),s.favoriteCount(),true,s.singers())).toList();
        return Map.of("success",true,"favorites",Map.of("list",liked,"total",dao.count(uid)));
    }
    public Object states(String uid,String ids) {
        if(ids==null || ids.length()>2100 || !ids.matches("[0-9]+(,[0-9]+){0,99}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"歌曲列表无效");
        var list=Arrays.stream(ids.split(",")).map(LibraryService::id).toList();return Map.of("success",true,"states",dao.states(uid,list));
    }
    @Transactional public Object set(String uid,String song,boolean liked) {
        id(song);if(!dao.lock(song,liked)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"歌曲不存在");
        dao.set(uid,song,liked);return Map.of("success",true);
    }
}
