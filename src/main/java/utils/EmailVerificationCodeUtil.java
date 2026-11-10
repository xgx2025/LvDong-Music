package utils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 邮件验证码服务类
 * 负责生成、发送、验证和管理验证码
 */
public class EmailVerificationCodeUtil {
    // 存储验证码信息的容器，key为邮箱，value为验证码信息
    private static final ConcurrentHashMap<String, CodeInfo> codeStore = new ConcurrentHashMap<>();
    // 验证码有效期（分钟）
    private static final long CODE_EXPIRE_MINUTES = 10;
    // 验证码长度
    private static final int CODE_LENGTH = 6;
    // 发送间隔限制（秒）- 防止频繁发送
    private static final long SEND_INTERVAL_SECONDS = 60;

    // 邮件服务器配置
    private static final String SMTP_HOST = ConfigUtil.get("SMTP_HOST", "smtp.host", "smtp.qq.com");
    private static final String SMTP_PORT = ConfigUtil.get("SMTP_PORT", "smtp.port", "465");
    private static final String SMTP_USER = ConfigUtil.get("SMTP_USER", "smtp.user");
    private static final String SMTP_PASSWORD = ConfigUtil.get("SMTP_PASSWORD", "smtp.password");
    private static final String SENDER_NAME = "律动音乐";

    /**
     * 验证码信息内部类
     * 增加最后发送时间字段用于控制发送频率
     */
    private static class CodeInfo {
        String code;         // 验证码
        long createTime;     // 创建时间（毫秒）
        long lastSendTime;   // 最后发送时间（毫秒）- 新增字段

        CodeInfo(String code) {
            this.code = code;
            this.createTime = System.currentTimeMillis();
            this.lastSendTime = System.currentTimeMillis(); // 初始化时记录发送时间
        }

        /**
         * 判断验证码是否过期
         */
        boolean isExpired() {
            return System.currentTimeMillis() - createTime >
                    TimeUnit.MINUTES.toMillis(CODE_EXPIRE_MINUTES);
        }

        /**
         * 判断是否可以重新发送（是否超过发送间隔）
         */
        boolean canResend() {
            return System.currentTimeMillis() - lastSendTime >
                    TimeUnit.SECONDS.toMillis(SEND_INTERVAL_SECONDS);
        }

        /**
         * 获取剩余冷却时间（秒）
         */
        long getRemainingCoolTime() {
            long elapsed = System.currentTimeMillis() - lastSendTime;
            long remaining = TimeUnit.SECONDS.toMillis(SEND_INTERVAL_SECONDS) - elapsed;
            return Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remaining));
        }
    }

    /**
     * 生成并发送验证码（添加频率控制）
     * @param email 目标邮箱
     * @return 包含验证码和状态的结果对象
     * @throws MessagingException 邮件发送异常
     */
    public static Result generateAndSendCode(String email) throws MessagingException, UnsupportedEncodingException {
        // 检查是否存在未过期的验证码且处于冷却期
        CodeInfo existingCode = codeStore.get(email);
        if (existingCode != null && !existingCode.canResend()) {
            return new Result(false, null,
                    "请不要频繁发送验证码，剩余" + existingCode.getRemainingCoolTime() + "秒");
        }

        // 生成验证码
        String code = generateRandomCode();
        CodeInfo codeInfo;

        // 如果存在未过期的验证码，更新发送时间而不生成新验证码
        if (existingCode != null && !existingCode.isExpired()) {
            codeInfo = existingCode;
            codeInfo.lastSendTime = System.currentTimeMillis();
            code = existingCode.code; // 使用原有验证码
        } else {
            // 新建验证码信息
            codeInfo = new CodeInfo(code);
        }

        // 存储验证码
        codeStore.put(email, codeInfo);

        // 发送邮件
        sendEmail(email, code);

        return new Result(true, code, "验证码发送成功");
    }

    /**
     * 验证验证码
     * @param email 邮箱
     * @param code 待验证的验证码
     * @return 验证结果
     */
    public static boolean verifyCode(String email, String code) {
        if (email == null || code == null) {
            return false;
        }

        CodeInfo codeInfo = codeStore.get(email);
        // 验证码不存在、已过期或不匹配
        if (codeInfo == null || codeInfo.isExpired() || !codeInfo.code.equals(code)) {
            return false;
        }

        // 验证成功后移除验证码，防止重复使用
        codeStore.remove(email);
        return true;
    }

    /**
     * 移除指定邮箱的验证码
     * @param email 邮箱
     */
    public static void removeCode(String email) {
        codeStore.remove(email);
    }

    /**
     * 生成随机验证码
     * @return 数字验证码
     */
    private static String generateRandomCode() {
        Random rand = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(rand.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 发送邮件
     * @param toEmail 接收邮箱
     * @param code 验证码
     * @throws MessagingException 邮件发送异常
     */
    private static void sendEmail(String toEmail, String code) throws MessagingException, UnsupportedEncodingException {
        // 配置邮件服务器
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        props.put("mail.smtp.timeout", "5000"); // 超时时间

        // 创建会话
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USER, SMTP_PASSWORD);
            }
        });
        session.setDebug(false); // 生产环境关闭调试

        // 创建邮件消息
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(SMTP_USER, SENDER_NAME, "UTF-8"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
        message.setSubject(SENDER_NAME + "验证码", "UTF-8");
        message.setSentDate(new java.util.Date());

        // 邮件内容
        String content = String.format(
                "【%s】您的验证码是：<strong>%s</strong><br/>" +
                        "该验证码10分钟内有效，请尽快完成验证。<br/>" +
                        "如非本人操作，请忽略此邮件。",
                SENDER_NAME, code
        );
        message.setContent(content, "text/html;charset=UTF-8");

        // 发送邮件
        Transport.send(message);
    }

    /**
     * 验证码操作结果封装类
     */
    public static class Result {
        private boolean success;    // 操作是否成功
        private String code;        // 验证码（成功时有效）
        private String message;     // 提示信息

        public Result(boolean success, String code, String message) {
            this.success = success;
            this.code = code;
            this.message = message;
        }

        // Getter方法
        public boolean isSuccess() { return success; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}