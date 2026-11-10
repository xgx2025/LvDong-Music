package servlet;

import annotation.ParamName;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.*;
import service.SearchService;
import service.impl.SearchServiceImpl;
import utils.JwtTokenUtil;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class SearchServlet extends BaseServlet{
    private static final Logger log = LoggerFactory.getLogger(SearchServlet.class);

    SearchService searchService = new SearchServiceImpl();

    protected Object search(@ParamName("accessToken") String accessToken,@ParamName("keyword") String keyword,@ParamName("page")int page,@ParamName("pageSize")int pageSize) throws ServletException, IOException {
        log.info("搜索关键词: {}", keyword);
        Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken,JwtTokenUtil.ACCESS_TOKEN_SECRET);
        String userId = (String)claims.get("id");
        Map<String,Object> result = new HashMap<>();
      try {
          SearchResult searchResult = searchService.comprehensiveSearch(keyword, page, pageSize,userId);
          result.put("success",true);
          result.put("message",searchResult);
          return result;
      } catch (Exception e) {
          result.put("success",false);
          result.put("message","查询失败");
          return result;
      }
    }

}
