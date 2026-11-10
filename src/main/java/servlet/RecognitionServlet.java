package servlet;

import annotation.ParamName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.Song;
import service.RecognitionService;
import service.impl.RecognitionServiceImpl;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

public class RecognitionServlet extends BaseServlet{
    private static final Logger log = LoggerFactory.getLogger(RecognitionServlet.class);

    RecognitionService recognitionService = new RecognitionServiceImpl();

    protected Object musicRecognition(@ParamName("text")String text){
        log.info("音乐识别输入文本: {}", text);
        Map<String,Object>result = new HashMap<>();
        try {
            Map<String, Object> recognitionResult = recognitionService.musicRecognition(text);
            Song song = (Song) recognitionResult.get("song");
            String songInfo = (String) recognitionResult.get("songInfo");
            if (song != null) {
                result.put("success", true);
                result.put("data", song);
                result.put("songInfo", songInfo);
            } else {
                result.put("success", true);
                result.put("message", "没有查询到有关歌曲，歌曲尚未被收录");
                result.put("songInfo", songInfo);
            }
        }catch (Exception e){
            log.error("歌曲识别失败", e);
            result.put("success", false);
            result.put("message", "歌曲识别失败！");
        }
        return result;
    }
}
