package com.hope.music.recognition.service;

import com.hope.music.library.dao.LibraryDao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Matches inferred names only to published songs in the local library. */
@Service
public class RecognitionService {
    private final RecognitionProvider provider;
    private final LibraryDao library;
    public RecognitionService(RecognitionProvider provider,LibraryDao library) { this.provider=provider;this.library=library; }
    public Object recognize(String text) {
        if(text==null||text.isBlank()||text.length()>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请输入1至1000个字符的歌词线索");
        var hint=provider.recognize(text.trim());
        var songs=library.songs("s.name=? AND EXISTS(SELECT 1 FROM song_singer ss WHERE ss.song_id=s.id AND ss.singer_name=?) ORDER BY s.id LIMIT 1",hint.song(),hint.singer());
        String info="《"+hint.song()+"》--- "+hint.singer();
        return songs.isEmpty()?Map.of("success",true,"songInfo",info,"message","没有查询到有关歌曲，歌曲尚未被收录"):
                Map.of("success",true,"songInfo",info,"data",songs.getFirst());
    }
}
