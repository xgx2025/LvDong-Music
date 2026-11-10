package utils.hummingEcognition;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class Ultra {
    private static final Logger log = LoggerFactory.getLogger(Ultra.class);

    public static String header = "Authorization: Bearer mPsPUEYNJaCYWHFzDmMG:HGoevBtsGfiaMTKKJrvJ"; // 替换为自己的密钥
    public static Gson gson = new Gson ();

    // 仅保存 AI 角色设定（system 消息），不存储任何用户或 AI 的历史记录
    private static final RoleContent systemRole;

    // AI 角色设定（保持核心逻辑，精简冗余描述以减少 token 消耗）
    private static final String SYSTEM_PROMPT = "" +
            "- Role: 音乐识别与分析专家 \n" +
            "- Background: 根据用户提供的哼唱识别文字（可能有误），推测歌曲名称和歌手。\n" +
            "- Skills: 歌词匹配、识别错误修正、歌曲数据库检索。\n" +
            "- OutputFormat:  严格返回JSON格式，仅包含字段{\"song\":\"歌曲名称\",\"singer\":\"歌手名称\"}，无额外内容。\n" +
            "- Examples:\n" +
            "- 输入：“夜空中最亮的星，请照亮我前行”\n" +
            "- 输出：{\"song\":\"夜空中最亮的星\",\"singer\":\"逃跑计划\"}\n";

    // 静态初始化 system 角色（仅创建一次）
    static {
        systemRole = new RoleContent ();
        systemRole.role = "system";
        systemRole.content = SYSTEM_PROMPT;
    }

    /**

     音乐识别方法，不保存任何询问记录，仅携带 AI 角色设定
     @param text 哼唱识别出的文字信息
     @return 包含歌曲和歌手信息的 JSONObject，异常时包含 error 字段
     */
    public static JSONObject musicRecognition (String text) {
        String userId = "10284711 用户";
        try {
            String url = "https://spark-api-open.xf-yun.com/v1/chat/completions";
            JSONObject jsonObject = new JSONObject ();
            jsonObject.put ("user", userId);
            jsonObject.put ("model", "4.0Ultra"); // 确保支持 system 角色
            JSONArray messagesArray = new JSONArray ();
            // 1. 仅添加 AI 角色设定
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", systemRole.role);
            systemMsg.put("content", systemRole.content);
            messagesArray.add(systemMsg);
            // 2. 添加当前用户输入
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", text);
            messagesArray.add(userMsg);
            jsonObject.put("messages", messagesArray);
            jsonObject.put("stream", false);
            jsonObject.put("max_tokens", 8192);
            jsonObject.put("temperature", 0.1);
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", header);
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            os.write(jsonObject.toString().getBytes());
            os.flush();
            os.close();
            int responseCode = con.getResponseCode ();
            if (responseCode != 200) {
                JSONObject errorJson = new JSONObject ();
                errorJson.put ("error", "API 请求失败，响应码：" + responseCode);
                return errorJson;
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            // 解析 API 响应
            JsonParse jsonParse = gson.fromJson (response.toString (), JsonParse.class);
            if (jsonParse == null || jsonParse.choices == null || jsonParse.choices.isEmpty ()) {
                JSONObject errorJson = new JSONObject ();
                errorJson.put ("error", "未获取到有效响应");
                return errorJson;
            }
            // 提取模型返回的 JSON 结果
            String modelContent = jsonParse.choices.get (0).message.content;
            log.info("星火API响应: {}", modelContent);
            return new JSONObject(modelContent);
        } catch (Exception e) {
            log.error("星火API请求处理异常", e);
            JSONObject errorJson = new JSONObject ();
            errorJson.put ("error", "处理请求时发生异常：" + e.getMessage ());
            return errorJson;
        }
    }

    // 内部静态类保持不变
    static class JsonParse {
        List<Choices> choices;
    }

    static class Choices {
        Message message;
    }

    static class Message {
        String content;
    }

    static class RoleContent {
        String role;
        String content;

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}