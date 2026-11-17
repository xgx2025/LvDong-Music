package com.hope.music.creator.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hope.music.creator.dao.CreatorDao;
import com.hope.music.library.service.LibraryService;
import com.hope.music.storage.service.FileStorageService;
import com.hope.music.storage.service.FileStorageService.FilePayload;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** File and database writes commit together; rollback removes only files created by this request. */
@Service
public class CreatorService {
    private final CreatorDao dao;
    private final FileStorageService storage;
    public CreatorService(CreatorDao dao, FileStorageService storage) { this.dao=dao; this.storage=storage; }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record ProfileDTO(String username,String gender,String bio,String avatarBase64) { }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record ApplicationDTO(String singerName,String gender,String nationality,String singerBio,String singerAvatarBase64,String idCardFrontBase64,String idCardBackBase64) { }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record UploadDTO(String songName,String singerIdStr,String duration,String album,FilePayload musicFile,FilePayload coverImage,FilePayload lyricFile) { }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,text); }
    private static String text(String value,int min,int max) { value=value==null?"":value.trim();if(value.length()<min||value.length()>max)throw bad("资料字段长度无效");return value; }
    private static String gender(String gender) { if(!Set.of("未知","男","女").contains(gender==null?"":gender))throw bad("性别无效");return gender; }
    private void lock(String id) { if(!dao.lockUser(id))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    public Object status(String id) { Integer state=dao.status(id);return Map.of("success",true,"status",state==null?"normal":state==0?"pending":state==1?"approved":"rejected"); }
    @Transactional
    public Object profile(String id,ProfileDTO body) {
        String name=text(body.username(),2,20), bio=text(body.bio(),0,500), gender=gender(body.gender());lock(id);
        String avatar=body.avatarBase64()==null||body.avatarBase64().isBlank()?null:storage.save(FileStorageService.image(body.avatarBase64()),"image",false).url();
        try { dao.updateProfile(id,name,gender,bio,avatar); }
        catch(DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"用户名已存在"); }
        return avatar==null?Map.of("success",true):Map.of("success",true,"avatarUrl",avatar);
    }
    @Transactional
    public Object apply(String id,ApplicationDTO body) {
        String name=text(body.singerName(),1,30), nationality=text(body.nationality(),1,10), bio=text(body.singerBio(),1,500), gender=gender(body.gender());lock(id);
        if(dao.status(id)!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"已提交音乐人申请，请勿重复提交");
        var avatar=storage.save(FileStorageService.image(body.singerAvatarBase64()),"image",false);
        var front=storage.save(FileStorageService.image(body.idCardFrontBase64()),"image",true);
        var back=storage.save(FileStorageService.image(body.idCardBackBase64()),"image",true);
        dao.apply(id,name,gender,nationality,bio,avatar.url(),front.path().toString(),back.path().toString());
        return Map.of("success",true);
    }
    @Transactional
    public Object upload(String id,UploadDTO body) {
        String name=text(body.songName(),1,30), album=text(body.album(),0,30);lock(id);
        if(!Integer.valueOf(1).equals(dao.status(id)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅审核通过的音乐人可以上传歌曲");
        if(body.duration()==null||!body.duration().matches("[0-9]{1,2}:[0-5][0-9]"))throw bad("时长格式必须为mm:ss");
        if(body.singerIdStr()==null||body.singerIdStr().length()>2100)throw bad("歌手ID无效");
        var singerIds=new TreeSet<String>();
        for(String value:body.singerIdStr().split(";",-1))singerIds.add(LibraryService.id(value.trim()));
        if(singerIds.size()>100||!singerIds.contains(id))throw bad("歌手ID必须包含本人");
        Map<String,String> singers=new LinkedHashMap<>();
        for(String singerId:singerIds) { String singer=dao.approvedSinger(singerId);if(singer==null)throw bad("歌手不存在或未认证");singers.put(singerId,singer); }
        var audio=storage.save(body.musicFile(),"audio",false);
        var cover=storage.save(body.coverImage(),"image",false);
        var lyric=body.lyricFile()==null?null:storage.save(body.lyricFile(),"lyric",false);
        String songId=String.valueOf(UUID.randomUUID().getMostSignificantBits()&Long.MAX_VALUE);
        dao.insertSong(songId,name,body.duration(),album,audio.path().toString(),cover.url(),lyric==null?null:lyric.url(),singers);
        return Map.of("success",true,"songId",songId);
    }
}
