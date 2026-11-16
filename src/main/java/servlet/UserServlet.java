package servlet;

import annotation.ParamName;
import com.google.gson.JsonObject;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pojo.User;
import service.UserService;
import service.impl.UserServiceImpl;
import utils.EmailVerificationCodeUtil;
import utils.JwtTokenUtil;
import utils.TokenBlacklist;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class UserServlet extends BaseServlet{
    private static final Logger log = LoggerFactory.getLogger(UserServlet.class);

    private UserService userService = new UserServiceImpl();

    /** Returns only the authenticated caller's public profile fields. */
    protected Object getProfile(@ParamName("accessToken") String accessToken) {
        Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
        User user = userService.getProfile(Long.valueOf(String.valueOf(claims.get("id"))));
        JsonObject response = new JsonObject();
        response.addProperty("success", user != null);
        if (user == null) {
            response.addProperty("message", "账号不存在，请重新登录");
            return response;
        }
        JsonObject profile = new JsonObject();
        profile.addProperty("id", String.valueOf(user.getId()));
        profile.addProperty("username", user.getUsername());
        profile.addProperty("email", user.getEmail());
        profile.addProperty("avatar", user.getAvatar());
        profile.addProperty("gender", user.getGender());
        profile.addProperty("bio", user.getBio());
        profile.addProperty("registerTime", user.getRegisterTime());
        response.add("user", profile);
        return response;
    }

    /**
     * 用户登录
     * @param email
     * @param password
     * @return
     * @throws ServletException
     * @throws IOException
     */
    protected Object login(@ParamName("email") String email, @ParamName("password") String password) throws ServletException, IOException {
        Map<String,Object> result = new HashMap<>();
        result.put("success",false);

        // 参数合法性校验（邮箱长度上限；密码不做长度拦截，避免误伤已注册的长口令用户）
        if (email == null || password == null || email.length() > 254) {
            result.put("message", "邮箱或密码错误");
            return result;
        }

        // 验证密码
        User user = userService.login(email, password);
        if (user == null) {
            // 不区分“邮箱不存在”与“密码错误”，避免账号枚举
            // 邮箱存在性判断已在 userService.login 内部完成，此处不额外查询
            log.warn("登录失败: email={}", email);
            result.put("message","邮箱或密码错误");
            return result;
        }
        log.info("用户登录成功: userId={}", user.getId());

        // 准备Token claims（仅包含必要的用户标识，不包含敏感信息）
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", String.valueOf(user.getId()));
        claims.put("username", user.getUsername());

        // 生成双Token
        String accessToken = JwtTokenUtil.generateAccessToken(claims);
        String refreshToken = JwtTokenUtil.generateRefreshToken(claims);

        JsonObject response = new JsonObject();  // 根响应对象
        // 1. 构建 tokens 对象（包含 accessToken、refreshToken、expiresIn）
        JsonObject tokens = new JsonObject();
        tokens.addProperty("accessToken", accessToken);  // 前端需要的 tokens.accessToken
        tokens.addProperty("refreshToken", refreshToken);  // 前端需要的 tokens.refreshToken
        tokens.addProperty("expiresIn", JwtTokenUtil.ACCESS_TOKEN_EXPIRE / 1000);  // 有效期（秒）
        response.add("tokens", tokens);  // 将 tokens 放入根响应

        // 2. 构建 user 对象（用户信息）
        JsonObject userInfo = new JsonObject();
        userInfo.addProperty("id", String.valueOf(user.getId()));
        userInfo.addProperty("username", user.getUsername());
        userInfo.addProperty("email", user.getEmail());
        userInfo.addProperty("avatar",user.getAvatar());
        userInfo.addProperty("gender",user.getGender());
        userInfo.addProperty("bio",user.getBio());
        userInfo.addProperty("registerTime",user.getRegisterTime());
        response.add("user", userInfo);  // 将 user 放入根响应
        response.addProperty("success", true);
        response.addProperty("message", "登录成功");
        return response;  // 返回 返回 { success: true, message: "登录成功", user: ..., tokens: ... } 结构
    }


    /**
     * 用户注册
     * @param email
     * @param password
     * @param verificationCode
     * @throws ServletException
     * @throws IOException
     */
    protected Object register(@ParamName("username")String username,@ParamName("email") String email,@ParamName("password") String password,@ParamName("verificationCode") String verificationCode) throws ServletException, IOException {
        log.info("用户注册: username={}, email={}", username, email);

        Map<String, Object> result = new HashMap<>();

        // 输入验证
        if (username == null || username.trim().length() < 2 || username.trim().length() > 20) {
            result.put("success", false);
            result.put("message", "用户名长度必须在2-20个字符之间");
            return result;
        }
        if (email == null || !email.matches("^[\\w.+-]+@[\\w-]+\\.[\\w.]+$")) {
            result.put("success", false);
            result.put("message", "邮箱格式不正确");
            return result;
        }
        if (password == null || password.length() < 8) {
            result.put("success", false);
            result.put("message", "密码长度不能少于8个字符");
            return result;
        }

        try {
            boolean isValid = EmailVerificationCodeUtil.verifyCode(email, verificationCode);
            log.debug("验证码验证结果: isValid={}", isValid);
            if (isValid) {
                result.put("success", true);
                result.put("message", "验证码验证成功");
                // 可添加后续逻辑：如标记邮箱已验证、生成令牌等
                User user = new User(username, email, password, 0);
                if (!userService.registerUser(user)) {
                    result.put("success", false);
                    result.put("message", "注册失败");
                }
            } else {
                result.put("success", false);
                result.put("message", "验证码无效或已过期");
            }
        }catch (RuntimeException e) {
            // 处理DAO层抛出的特定异常
            result.put("success", false);
            // 检查是否是用户名重复异常
            if (e.getMessage() != null && e.getMessage().contains("用户名已存在")) {
                result.put("message", "用户名已存在，请选择其他用户名");
            } else if (e.getMessage() != null && e.getMessage().contains("该邮箱已注册")){
                result.put("message", "该邮箱已注册");
            }else {
                // 其他运行时异常
                result.put("message", "注册失败，请稍后再试");
            }
            log.error("用户注册失败: email={}", email, e);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "注册失败，请稍后再试");
            log.error("用户注册异常: email={}", email, e);
        }
        return result;

    }

    /**
     * 重置账号密码
     * @param email
     * @param newPassword
     * @param verificationCode
     * @return
     * @throws ServletException
     * @throws IOException
     */
    protected Object resetPassword(@ParamName("email")String email,@ParamName("newPassword")String newPassword,@ParamName("verificationCode")String verificationCode) throws ServletException, IOException {
        Map<String, Object> result = new HashMap<>();

        // 输入验证
        if (newPassword == null || newPassword.length() < 8) {
            result.put("success", false);
            result.put("message", "密码长度不能少于8个字符");
            return result;
        }

        try {
            boolean isValid = EmailVerificationCodeUtil.verifyCode(email, verificationCode);
            log.debug("重置密码验证码验证结果: isValid={}", isValid);
            if (isValid) {
                result.put("success", true);
                result.put("message", "验证码验证成功");
                if (!userService.resetPassword(email,newPassword))
                {
                    result.put("success", false);
                    result.put("message", "该邮箱尚未注册");
                }else{
                    result.put("message","密码重置成功");
                }
            } else {
                result.put("success", false);
                result.put("message", "验证码无效或已过期");
            }
        }catch (RuntimeException e) {
            // 处理DAO层抛出的特定异常
            result.put("success", false);
            // 其他运行时异常
            result.put("message", "重置密码失败，请稍后再试");
            log.error("重置密码失败: email={}", email, e);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置密码失败，请稍后再试");
            log.error("重置密码异常: email={}", email, e);
        }
        return result;
    }

    /**
     * 用户登出
     * 1. 接收前端的登出请求
     * 2. （可选）将当前 token 加入黑名单（如果需要服务器端验证）
     * 3. 返回成功响应
     */
    protected Object logout(HttpServletRequest request, @ParamName("refreshToken") String refreshTokenParam) {
        try {
            String authHeader = request.getHeader("Authorization");
            if ((authHeader != null && authHeader.startsWith("Bearer b1")) ||
                    (refreshTokenParam != null && refreshTokenParam.startsWith("b1"))) {
                new dao.impl.BootSessionDao().revoke(authHeader == null ? null : authHeader.substring(7), refreshTokenParam);
                JsonObject response = new JsonObject(); response.addProperty("success", true); return response;
            }
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String accessToken = authHeader.substring(7);
                // 将access token加入黑名单
                try {
                    Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
                    TokenBlacklist.add(accessToken, claims.getExpiration().getTime());
                } catch (RuntimeException invalidAccessToken) {
                    // 无效/过期 access token 不妨碍撤销仍有效的 refresh token。
                }
            }
            // 同时将refresh token加入黑名单（如果在请求体中提供）
            // refreshToken 由 BaseServlet 统一从 JSON 或表单参数绑定。
            if (refreshTokenParam != null && !refreshTokenParam.isEmpty()) {
                if (!JwtTokenUtil.isTokenExpired(refreshTokenParam, JwtTokenUtil.REFRESH_TOKEN_SECRET)) {
                    Claims claims = JwtTokenUtil.getClaimsFromToken(refreshTokenParam, JwtTokenUtil.REFRESH_TOKEN_SECRET);
                    TokenBlacklist.add(refreshTokenParam, claims.getExpiration().getTime());
                }
            }

            // 4. 构建成功响应
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "登出成功");
            return response;
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("message", "登出失败，请稍后再试");
            return error;
        }
    }

    /**
     * 刷新 accessToken,保留 refreshToken
     * @param refreshToken
     * @return
     */
    protected JsonObject refreshToken(@ParamName("refreshToken") String refreshToken) {
        log.info("正在刷新token");
        try {
            if (refreshToken == null || refreshToken.isBlank() || refreshToken.startsWith("b1") || TokenBlacklist.contains(refreshToken)) {
                throw new IllegalArgumentException("刷新凭证已撤销或缺失");
            }
            // 1. 验证refreshToken的有效性
            Claims claims = JwtTokenUtil.getClaimsFromToken(refreshToken, JwtTokenUtil.REFRESH_TOKEN_SECRET);
            if (claims == null) {
                throw new IllegalArgumentException("无效的refreshToken");
            }

            if (JwtTokenUtil.isTokenExpired(refreshToken, JwtTokenUtil.REFRESH_TOKEN_SECRET)) {
                log.warn("refreshToken已过期");
                throw new IllegalArgumentException("refreshToken已过期");
            }

            // 2. 从refreshToken中提取用户信息
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", claims.get("id"));
            userInfo.put("username", claims.get("username"));

            // 3. 生成新的accessToken
            String newAccessToken = JwtTokenUtil.generateAccessToken(userInfo);
            String newRefreshToken = refreshToken; // 固定期限：退出可以撤销同一会话的刷新凭证
            // 4. 构建响应
            JsonObject response = new JsonObject();
            JsonObject tokens = new JsonObject();
            tokens.addProperty("accessToken", newAccessToken);
            tokens.addProperty("refreshToken", newRefreshToken);
            response.add("tokens", tokens);

            log.info("token续期成功");
            return response;
        } catch (SignatureException e) {
            log.error("刷新Token失败：签名验证错误", e);
            throw new IllegalArgumentException("刷新Token失败：签名验证错误");
        } catch (ExpiredJwtException e) {
            log.error("刷新Token失败：token已过期", e);
            throw new IllegalArgumentException("刷新Token失败：token已过期");
        } catch (Exception e) {
            log.error("刷新Token失败: {}", e.getMessage(), e);
            throw new IllegalArgumentException("刷新Token失败，请重新登录");
        }
    }

    /**
     * 更新用户信息
     * @param accessToken
     * @param username
     * @param gender
     * @param bio
     * @param avatarBase64
     * @return
     */
    protected Object updateProfile(@ParamName("accessToken") String accessToken, @ParamName("username")String username, @ParamName("gender")String gender, @ParamName("bio")String bio, @ParamName("avatarBase64")String avatarBase64) {
        Map<String, Object> response = new HashMap<>();

        // 输入验证
        if (username != null && (username.trim().length() < 2 || username.trim().length() > 20)) {
            response.put("success", false);
            response.put("message", "用户名长度必须在2-20个字符之间");
            return response;
        }
        if (bio != null && bio.length() > 200) {
            response.put("success", false);
            response.put("message", "个人简介不能超过200个字符");
            return response;
        }

        Claims claims = JwtTokenUtil.getClaimsFromToken(accessToken, JwtTokenUtil.ACCESS_TOKEN_SECRET);
        String userId = (String) claims.get("id");
        userId = String.valueOf(userId);
        if(userId == null){
            response.put("success", false);
            response.put("message", "资料更新失败:获取不到用户ID");
            log.warn("资料更新失败:获取不到用户ID");
            return response;
        }

        User user = new User();
        user.setId(Long.parseLong(userId));
        user.setUsername(username);
        user.setGender(gender);
        user.setBio(bio);
        user.setAvatar(avatarBase64);
        // 直接返回 Service 层的结果，不再进行类型转换
        return userService.updateUserProfile(user);
    }
}
