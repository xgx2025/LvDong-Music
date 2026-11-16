package utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.Map;

public class JwtTokenUtil {

    // 从配置读取密钥和过期时间
    public static final String ACCESS_TOKEN_SECRET = ConfigUtil.get("JWT_ACCESS_SECRET", "jwt.access.secret");
    public static final long ACCESS_TOKEN_EXPIRE = ConfigUtil.getLong("JWT_ACCESS_EXPIRE", "jwt.access.expire", 30 * 60 * 1000L);

    public static final String REFRESH_TOKEN_SECRET = ConfigUtil.get("JWT_REFRESH_SECRET", "jwt.refresh.secret");
    public static final long REFRESH_TOKEN_EXPIRE = ConfigUtil.getLong("JWT_REFRESH_EXPIRE", "jwt.refresh.expire", 7 * 24 * 60 * 60 * 1000L);


    /**
     * 生成Access Token
     */
    public static String generateAccessToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE))
                .signWith(SignatureAlgorithm.HS256, ACCESS_TOKEN_SECRET)
                .compact();
    }

    /**
     * 生成Refresh Token
     */
    public static String generateRefreshToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE))
                .signWith(SignatureAlgorithm.HS256, REFRESH_TOKEN_SECRET)
                .compact();
    }

    /**
     * 验证Token并获取claims
     */
    public static Claims getClaimsFromToken(String token, String secret) {
        if (token != null && token.startsWith("b1")) {
            if (!java.util.Objects.equals(secret, ACCESS_TOKEN_SECRET))
                throw new IllegalArgumentException("Boot刷新凭证只能由Boot处理");
            return new dao.impl.BootSessionDao().validateAccess(token);
        }
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 检查Token是否过期
     */
    public static boolean isTokenExpired(String token, String secret) {
        try {
            Claims claims = getClaimsFromToken(token, secret);
            Date expiration = claims.getExpiration();
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            // 明确捕获过期异常
            return true;
        } catch (Exception e) {
            // 其他异常不直接视为过期，而是抛出具体异常
            throw new IllegalArgumentException("token验证失败: " + e.getMessage());
        }
    }


}