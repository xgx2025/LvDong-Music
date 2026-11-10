package servlet;

import annotation.ParamName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.EmailVerificationCodeUtil;
import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class AuthServlet extends BaseServlet {
    private static final Logger log = LoggerFactory.getLogger(AuthServlet.class);

    /**
     * 发送邮箱验证码接口
     * 支持表单提交和JSON请求，参数为email
     * 返回Map结构数据，由BaseServlet统一处理响应
     */
    protected Map<String, Object> sendVerificationCode(@ParamName("email") String email) throws ServletException, IOException {
        Map<String, Object> result = new HashMap<>();

        try {
            // 2. 校验参数（前端已校验，后端做基础兜底）
            if (email == null || email.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "邮箱参数不能为空");
                return result;
            }

            // 3. 调用工具类生成并发送验证码（修改点）
            EmailVerificationCodeUtil.Result codeResult = EmailVerificationCodeUtil.generateAndSendCode(email);

            // 4. 构建响应（修改点）
            result.put("success", codeResult.isSuccess());
            result.put("message", codeResult.getMessage());

            // 调试用（生产环境删除）
            if (codeResult.isSuccess()) {
                log.info("验证码已生成并发送至邮箱: {}", email);
            }

        } catch (MessagingException e) {
            // 邮件发送失败（如SMTP配置错误、网络问题）
            result.put("success", false);
            result.put("message", "验证码发送失败，请稍后重试");
            log.error("验证码邮件发送失败: email={}", email, e);
        } catch (Exception e) {
            // 其他异常
            result.put("success", false);
            result.put("message", "系统异常：" + e.getMessage());
            log.error("发送验证码时发生系统异常: email={}", email, e);
        }

        return result;
    }




    /**
     * 验证验证码接口
     * 支持表单提交和JSON请求，参数为email和code
     * 返回Map结构数据，由BaseServlet统一处理响应
     */
    protected Map<String, Object> verifyCode(@ParamName("email") String email,
                                             @ParamName("code") String code) throws ServletException, IOException {
        Map<String, Object> result = new HashMap<>();

        try {


            // 1. 基础校验
            if (email == null || email.trim().isEmpty() || code == null || code.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "邮箱和验证码参数不能为空");
                return result;
            }

            // 2. 调用工具类验证
            boolean isValid = EmailVerificationCodeUtil.verifyCode(email, code);

            // 3. 构建响应
            if (isValid) {
                result.put("success", true);
                result.put("message", "验证码验证成功");
                // 可添加后续逻辑：如标记邮箱已验证、生成令牌等
            } else {
                result.put("success", false);
                result.put("message", "验证码无效或已过期");
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "验证失败：" + e.getMessage());
            log.error("验证码验证失败: email={}", email, e);
        }

        return result;
    }

}