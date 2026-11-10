package servlet;

import annotation.ParamName;
import com.google.gson.JsonObject;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.Singer;
import pojo.Song;
import service.MusicService;
import service.impl.MusicServiceImpl;
import utils.JwtTokenUtil;
import java.util.*;

public class MusicServlet extends BaseServlet{
    private static final Logger log = LoggerFactory.getLogger(MusicServlet.class);

    MusicService musicService = new MusicServiceImpl();

    protected Object upload(
            @ParamName("accessToken") String accessToken,
            @ParamName("songName") String songName,
            @ParamName("singerIdStr") String singerIdStr,
            @ParamName("duration") String duration,
            @ParamName("album") String album,
            @ParamName("musicFile") JsonObject musicFile,
            @ParamName("lyricFile") JsonObject lyricFile,
            @ParamName("coverImage") JsonObject coverImage
    ) {
        Map<String, Object> result = new HashMap<>();

        // 输入验证
        if (songName == null || songName.trim().isEmpty() || songName.trim().length() > 100) {
            result.put("success", false);
            result.put("message", "歌曲名称不能为空且不能超过100个字符");
            return result;
        }
        if (singerIdStr == null || singerIdStr.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "歌手ID不能为空");
            return result;
        }
        if (duration == null || !duration.matches("^\\d{1,3}:\\d{2}$")) {
            result.put("success", false);
            result.put("message", "时长格式不正确，请使用 mm:ss 格式");
            return result;
        }
        // 验证singerIdStr中每个ID都是数字
        for (String id : singerIdStr.split(";")) {
            if (!id.trim().matches("^\\d+$")) {
                result.put("success", false);
                result.put("message", "歌手ID格式不正确");
                return result;
            }
        }

        // 2. 按分号解析成数组，并清理空白字符
        String[] singerIds = singerIdStr.split(";");

        log.info("解析后的 singerIds: {}", Arrays.toString(singerIds));

        // 3. 检查是否包含当前用户的 ID
        Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
        String userId = (String) claims.get("id");
        boolean existUserId = false;
        List<Singer> singers = new ArrayList<>();
        for (String singerId : singerIds) {
          Singer singer = new Singer();
            singer.setId(singerId);
            singers.add(singer);
            // 直接比较当前 singerId 和 userId
            if (userId.equals(singerId)) {
                existUserId = true;
            }
        }

        if (!existUserId) {
            result.put("success", false);
            result.put("message", "歌手ID中必须有一个是自己的ID！");
            return result;
        }
        // 4. 创建 Song 对象并保存
        Song song = new Song();
        song.setSongName(songName);
        song.setSingers(singers);
        song.setAlbumName(album);
        song.setDuration(duration);

        try {
            musicService.upload(song, musicFile, lyricFile, coverImage);
            result.put("success", true);
        } catch (Exception e) {
            log.error("音乐上传失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "上传失败，请稍后再试");
        }

        return result;
    }
//
}
