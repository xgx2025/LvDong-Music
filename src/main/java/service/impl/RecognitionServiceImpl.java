package service.impl;

import cn.hutool.json.JSONObject;
import dao.MusicDao;
import dao.impl.MusicDaoImpl;
import pojo.Song;
import service.RecognitionService;
import utils.hummingEcognition.Ultra;

import java.util.HashMap;
import java.util.Map;

public class RecognitionServiceImpl implements RecognitionService {
    MusicDao musicDao = new MusicDaoImpl();

    @Override
    public Map<String, Object> musicRecognition(String text) {
        JSONObject songInfo = Ultra.musicRecognition(text);
        String songName = (String) songInfo.get("song");
        String singerName = (String)songInfo.get("singer");
        String songInfoStr = "《"+songName+"》"+"--- "+singerName;
        Map<String,Object> result = new HashMap<>();
        Song song =  musicDao.findSongBySingerAndSongName(singerName,songName);
        result.put("song",song);
        result.put("songInfo",songInfoStr);

        return result;
    }
}
