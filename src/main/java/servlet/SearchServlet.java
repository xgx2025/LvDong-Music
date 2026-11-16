package servlet;

import annotation.ParamName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.*;
import service.SearchService;
import service.impl.SearchServiceImpl;
import utils.JwtTokenUtil;
import utils.PaginationUtil;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class SearchServlet extends BaseServlet{
    private static final Logger log = LoggerFactory.getLogger(SearchServlet.class);

    SearchService searchService = new SearchServiceImpl();

    protected Object search(@ParamName("accessToken") String accessToken,@ParamName("keyword") String keyword,@ParamName("page")int page,@ParamName("pageSize")int pageSize) throws ServletException, IOException {
        Map<String,Object> result = new HashMap<>();
        // 分页参数钳制：避免 pageSize 被任意放大导致大查询
        page = PaginationUtil.page(page);
        pageSize = PaginationUtil.size(pageSize);

        try {
            // 搜索允许匿名访问，token 无效时按未登录处理，不影响结果返回
            String userId = null;
            if (accessToken != null && !accessToken.isEmpty()) {
                userId = (String) JwtTokenUtil
                        .getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET).get("id");
            }
            log.info("搜索关键词: {}, page={}, pageSize={}", keyword, page, pageSize);

            SearchResult searchResult = searchService.comprehensiveSearch(keyword, page, pageSize, userId);
            result.put("success",true);
            result.put("message",searchResult);
            return result;
        } catch (Exception e) {
            log.warn("搜索失败: keyword={}, {}", keyword, e.getMessage());
            result.put("success",false);
            result.put("message","查询失败");
            return result;
        }
    }

}
