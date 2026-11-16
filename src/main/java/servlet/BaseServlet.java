package servlet;

import annotation.ParamName;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class BaseServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(BaseServlet.class);

    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd HH:mm:ss")
            .create();

    private static final Set<String> BLOCKED_METHODS = new HashSet<>(Arrays.asList(
            "getClass", "hashCode", "equals", "toString", "notify", "notifyAll", "wait",
            "doGet", "doPost", "processRequest", "findActionMethod",
            "prepareMethodArguments", "handleException"
    ));

    /** JSON 请求体上限：上传接口走 Base64 内嵌，允许较大，但不能无限制 */
    private static final int MAX_JSON_BODY_CHARS = 64 * 1024 * 1024;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.info("=== 收到GET请求 ===");
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        log.info("=== 收到POST请求 ===");
        processRequest(req, resp);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        try {
            // 1. 解析请求数据
            RequestData requestData = parseRequestData(req);

            // 2. 获取action参数
            String action = requestData.getParameter("action");
            log.info("请求action: {}", action);
            if (action == null || action.trim().isEmpty()) {
                sendErrorResponse(resp, "缺少参数", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // 3. 查找匹配的方法
            Method method = findActionMethod(action);
            if (method == null) {
                sendErrorResponse(resp, "未找方法: " + action, HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // 4. 准备方法参数
            Object[] args = prepareMethodArguments(method, req, resp, requestData);

            // 5. 调用方法并处理结果
            Object result = method.invoke(this, args);

            // 6. 发送响应
            sendResponse(resp, result);

        } catch (Exception e) {
            handleException(resp, e);
        }
    }

    /**
     * 解析请求数据，支持表单参数和JSON格式
     */
    private RequestData parseRequestData(HttpServletRequest req) throws IOException {
        RequestData data = new RequestData();

        // 1. 处理URL参数（原有逻辑不变）
        Enumeration<String> parameterNames = req.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            data.addParameter(paramName, req.getParameter(paramName));
        }

        // 2. 处理请求头（新增逻辑）
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            data.addHeader(headerName, req.getHeader(headerName)); // 将请求头存入RequestData
        }

        // 3. 处理JSON请求体
        String contentType = req.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            StringBuilder buffer = new StringBuilder();
            BufferedReader reader = req.getReader();
            String line;
            int totalChars = 0;
            while ((line = reader.readLine()) != null) {
                totalChars += line.length();
                if (totalChars > MAX_JSON_BODY_CHARS) {
                    // 防止超大 JSON 直接把堆打满
                    throw new IllegalArgumentException("请求体过大");
                }
                buffer.append(line);
            }
            String jsonStr = buffer.toString();
            if (!jsonStr.isEmpty()) {
                JsonObject jsonObject = GSON.fromJson(jsonStr, JsonObject.class);
                data.setJsonData(jsonObject);
            }
        }

        return data;
    }

    /**
     * 根据action名称查找匹配的方法
     */
    private Method findActionMethod(String action) {
        if (BLOCKED_METHODS.contains(action)) {
            return null;
        }
        Method[] methods = this.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equalsIgnoreCase(action)) {
                return method;
            }
        }
        return null;
    }

    /**
     * 准备方法参数
     */
    private Object[] prepareMethodArguments(Method method, HttpServletRequest req,
                                            HttpServletResponse resp, RequestData requestData) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> paramType = param.getType();

            // 1. 处理HttpServletRequest和HttpServletResponse
            if (paramType == HttpServletRequest.class) {
                args[i] = req;
                continue;
            }
            if (paramType == HttpServletResponse.class) {
                args[i] = resp;
                continue;
            }

            // 2. 获取参数名（从@ParamName注解）
            String paramName = param.getName();
            ParamName paramNameAnnotation = param.getAnnotation(ParamName.class);
            if (paramNameAnnotation != null) {
                paramName = paramNameAnnotation.value();
            }

            // 3. 优先从请求头中获取参数
            String headerValue = requestData.getHeader(paramName);
            if (headerValue != null) {
                log.debug("从请求头获取参数值: {}", headerValue);
                args[i] = convertParameterValue(headerValue, paramType);
                continue;
            }
            // 特殊处理Authorization头
            if ("accessToken".equals(paramName) && paramType == String.class) {
                String authHeader = req.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    args[i] = authHeader.substring(7); // 提取token部分
                    continue;
                }
            }
            // 4. 从JSON数据注入
            if (requestData.hasJsonData()) {
                JsonElement jsonValue = requestData.getJsonValue(paramName);
                if (jsonValue != null && !jsonValue.isJsonNull()) {
                    args[i] = convertJsonValue(jsonValue, paramType);
                    continue;
                }
            }

            // 5. 从表单参数注入
            String paramValue = requestData.getParameter(paramName);
            if (paramValue != null) {
                args[i] = convertParameterValue(paramValue, paramType);
                continue;
            }

            // 6. 复杂对象自动注入
            if (!isSimpleType(paramType)) {
                args[i] = createAndPopulateObject(paramType, requestData);
                continue;
            }

            // 7. 参数不存在，使用默认值
            args[i] = getDefaultValue(paramType);
        }

        return args;
    }

    /**
     * 将JSON值转换为目标类型
     */
    private Object convertJsonValue(JsonElement jsonValue, Class<?> targetType) {
        if (jsonValue == null || jsonValue.isJsonNull()) {
            return null;
        }

        try {
            if (targetType == String.class) {
                return jsonValue.getAsString();
            } else if (targetType == Integer.class || targetType == int.class) {
                return jsonValue.getAsInt();
            } else if (targetType == Long.class || targetType == long.class) {
                return jsonValue.getAsLong();
            } else if (targetType == Double.class || targetType == double.class) {
                return jsonValue.getAsDouble();
            } else if (targetType == Float.class || targetType == float.class) {
                return jsonValue.getAsFloat();
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return jsonValue.getAsBoolean();
            } else if (targetType == Date.class) {
                try {
                    String dateStr = jsonValue.getAsString();
                    try {
                        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(dateStr);
                    } catch (ParseException e) {
                        return new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid date format: " + jsonValue, e);
                }
            } else if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, jsonValue.getAsString());
            } else {
                // 复杂对象直接使用Gson转换
                return GSON.fromJson(jsonValue, targetType);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert JSON value to " + targetType.getName(), e);
        }
    }

    /**
     * 将字符串参数值转换为目标类型
     */
    private Object convertParameterValue(String value, Class<?> targetType) {
        if (value == null || value.isEmpty()) {
            return getDefaultValue(targetType);
        }

        try {
            if (targetType == String.class) {
                return value;
            } else if (targetType == Integer.class || targetType == int.class) {
                return Integer.parseInt(value);
            } else if (targetType == Long.class || targetType == long.class) {
                return Long.parseLong(value);
            } else if (targetType == Double.class || targetType == double.class) {
                return Double.parseDouble(value);
            } else if (targetType == Float.class || targetType == float.class) {
                return Float.parseFloat(value);
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                return Boolean.parseBoolean(value);
            } else if (targetType == Date.class) {
                try {
                    try {
                        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
                    } catch (ParseException e) {
                        return new SimpleDateFormat("yyyy-MM-dd").parse(value);
                    }
                } catch (ParseException e) {
                    throw new IllegalArgumentException("日期格式无效: " + value, e);
                }
            } else if (targetType.isEnum()) {
                return Enum.valueOf((Class<Enum>) targetType, value);
            } else {
                throw new IllegalArgumentException("不支持的参数类型: " + targetType.getName());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无法将参数值转换为 " + targetType.getName(), e);
        }
    }

    /**
     * 创建并填充复杂对象
     */
    private Object createAndPopulateObject(Class<?> targetType, RequestData requestData) {
        try {
            // 如果有JSON数据，优先从JSON创建对象
            if (requestData.hasJsonData()) {
                return GSON.fromJson(requestData.getJsonData(), targetType);
            }

            // 否则从表单参数创建对象
            Object instance = targetType.getDeclaredConstructor().newInstance();
            Field[] fields = targetType.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();

                String paramValue = requestData.getParameter(fieldName);
                if (paramValue != null) {
                    field.set(instance, convertParameterValue(paramValue, fieldType));
                }
            }

            return instance;
        } catch (Exception e) {
            throw new RuntimeException("无法创建和填充对象: " + targetType.getName(), e);
        }
    }

    /**
     * 检查是否为简单类型
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() || type == String.class || type == Integer.class ||
                type == Long.class || type == Double.class || type == Float.class ||
                type == Boolean.class || type == Date.class || type.isEnum();
    }

    /**
     * 获取类型的默认值
     */
    private Object getDefaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\u0000';
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        return null;
    }

    /**
     * 发送成功响应
     */
    private void sendResponse(HttpServletResponse resp, Object result) throws IOException {
        // 流式接口（HLS 等）会自行写响应，此时不能再改状态码或追加 JSON
        if (resp.isCommitted()) {
            return;
        }
        if (result == null) {
            resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        String jsonResponse = GSON.toJson(result);
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        out.print(jsonResponse);
        out.flush();
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(HttpServletResponse resp, String message, int statusCode) throws IOException {
        if (resp.isCommitted()) {
            return;
        }
        resp.setStatus(statusCode);
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("error", true);
        errorResponse.addProperty("message", message);
        errorResponse.addProperty("status", statusCode);

        PrintWriter out = resp.getWriter();
        out.print(GSON.toJson(errorResponse));
        out.flush();
    }

    /**
     * 处理异常
     */
    private void handleException(HttpServletResponse resp, Exception e) throws IOException {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        int statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        String errorMessage;

        if (cause instanceof IllegalArgumentException) {
            statusCode = HttpServletResponse.SC_BAD_REQUEST;
            errorMessage = "请求参数错误";
        } else if (cause instanceof NoSuchMethodException) {
            statusCode = HttpServletResponse.SC_NOT_FOUND;
            errorMessage = "未找到请求的方法";
        } else {
            errorMessage = "服务器内部错误";
        }

        log.error("Servlet处理异常: {}", cause.getMessage(), cause);

        sendErrorResponse(resp, errorMessage, statusCode);
    }

    /**
     * 封装请求数据
     */
    private static class RequestData {
        private final Map<String, String> parameters = new HashMap<>(); // 表单/URL参数
        private final Map<String, String> headers = new HashMap<>();    // 请求头
        private JsonObject jsonData;

        // 添加请求头
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        // 获取请求头
        public String getHeader(String name) {
            return headers.get(name);
        }

        public void addParameter(String name, String value) {
            parameters.put(name, value);
        }

        public void setJsonData(JsonObject jsonData) {
            this.jsonData = jsonData;
        }

        public boolean hasJsonData() {
            return jsonData != null;
        }

        public String getParameter(String name) {
            return parameters.get(name);
        }

        public JsonElement getJsonValue(String name) {
            return jsonData != null ? jsonData.get(name) : null;
        }

        public JsonObject getJsonData() {
            return jsonData;
        }
    }
}