package servlet;

import annotation.ParamName;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.FavoriteService;
import service.impl.FavoriteServiceImpl;
import utils.JwtTokenUtil;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class FavoriteServlet extends BaseServlet{
    private static final Logger log = LoggerFactory.getLogger(FavoriteServlet.class);

    FavoriteService favoriteService = new FavoriteServiceImpl();

    protected Map<String, Object> addFavorite(@ParamName("accessToken") String accessToken, @ParamName("songId")String songId) throws ServletException, IOException {
        Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken,JwtTokenUtil.ACCESS_TOKEN_SECRET);
        String userId = (String)claims.get("id");
        Map<String,Object> result = new HashMap<>();
        try {
            log.info("添加收藏: songId={}, userId={}", songId, userId);
           boolean success = favoriteService.addFavorite(Long.parseLong(songId),Long.parseLong(userId));
           if (success){
               result.put("success",true);
               result.put("message","收藏成功！");
           }else{
               result.put("success",false);
               result.put("message","收藏失败！");
           }
        } catch (SQLException e) {
            log.error("添加收藏失败: songId={}, userId={}", songId, userId, e);
            result.put("success",false);
            result.put("messag",e.getMessage());
            return result;
        }
        return result;
    }

    protected Object removeFavorite(@ParamName("accessToken") String accessToken, @ParamName("songId")String songId) throws ServletException, IOException {
        Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken,JwtTokenUtil.ACCESS_TOKEN_SECRET);
        String userId = (String)claims.get("id");
        Map<String,Object> result = new HashMap<>();
        try {
           boolean success = favoriteService.removeFavorite(Long.parseLong(songId),Long.parseLong(userId));
            if (success){
                result.put("success",true);
                result.put("message","取消收藏成功！");
            }else{
                result.put("success",false);
                result.put("message","取消收藏失败！");
            }
        } catch (SQLException e) {
            log.error("取消收藏失败: songId={}, userId={}", songId, userId, e);
            result.put("success",false);
            result.put("messag",e.getMessage());
            return result;
        }
        return result;
    }
}
