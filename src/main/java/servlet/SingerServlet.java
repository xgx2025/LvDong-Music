package servlet;

import annotation.ParamName;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.Singer;
import service.SingerService;
import service.impl.SingerServiceImpl;
import utils.JwtTokenUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SingerServlet extends BaseServlet{
    private static final Logger log = LoggerFactory.getLogger(SingerServlet.class);

    SingerService singerService = new SingerServiceImpl();

    protected Object applyMusician(@ParamName("accessToken") String accessToken, @ParamName("singerName")String singerName, @ParamName("gender")String gender, @ParamName("nationality")String nationality, @ParamName("singerBio")String singerBio,@ParamName("singerAvatarBase64")String singerAvatarBase64, @ParamName("idCardFrontBase64")String idCardFrontBase64, @ParamName("idCardBackBase64")String idCardBackBase64) {
        Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
        String userId = (String) claims.get("id");
        log.info("申请音乐人: userId={}, singerName={}", userId, singerName);
        Singer singer = new Singer();
        singer.setId(userId);
        singer.setName(singerName);
        singer.setGender(gender);
        singer.setIntro(singerBio);
        singer.setNationality(nationality);
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = singerService.applyMusician(singer, singerAvatarBase64, idCardFrontBase64, idCardBackBase64);
            if (success) {
                result.put("success", true);
            } else {
                result.put("success", false);
                result.put("message", "资料提交失败");
            }
        }catch (RuntimeException e){
            result.put("success", false);
            if (e.getMessage().contains("该用户已申请音乐人")) {
                result.put("message", "该用户已申请音乐人，不要重复申请");
            }else {
                result.put("message", e.getMessage());
            }
            log.error("申请音乐人失败: userId={}", userId, e);
        }
        return result;
    }


    protected Object getStatus(@ParamName("singerId")String singerId)  {
        Map<String, Object> result = new HashMap<>();
        try {
            int status = singerService.getStatus(singerId);
            if(status==0) {
                result.put("status", "pending");
            }else if (status == 1){
                result.put("status", "approved");
            }else{
                result.put("status", "rejected");
            }
        } catch (RuntimeException e) {
            result.put("status", "normal");
        }
        log.debug("获取歌手状态结果: {}", result);
        return result;
    }
}
