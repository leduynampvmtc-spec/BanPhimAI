package vn.leduynam.banphimai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Gọi Gemini hoặc Claude. Hàm chạy đồng bộ — luôn gọi trong luồng nền. */
public final class AiClient {

    private static final String SYSTEM =
            "Bạn là trợ lý chỉnh sửa văn bản được gắn trong bàn phím điện thoại. "
          + "Làm đúng yêu cầu với đoạn văn bản người dùng gửi. "
          + "Chỉ trả về đúng văn bản kết quả để dán thẳng vào ô nhập: "
          + "không giải thích, không lời chào, không đặt trong dấu ngoặc kép, không dùng markdown.";

    private AiClient() {}

    public static String run(Context c, String instruction, String text) throws Exception {
        String user = instruction + "\n\nVăn bản:\n" + text;
        String p = Prefs.provider(c);
        String out = Prefs.CLAUDE.equals(p) ? claude(c, user) : gemini(c, user);
        return clean(out);
    }

    private static String gemini(Context c, String user) throws Exception {
        String key = Prefs.geminiKey(c);
        if (key.isEmpty()) throw new Exception("Chưa nhập API key Gemini. Mở app Bàn phím AI để nhập.");
        String model = Prefs.geminiModel(c);
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                + URLEncoder.encode(model, "UTF-8") + ":generateContent");

        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject()
                .put("parts", new JSONArray().put(new JSONObject().put("text", SYSTEM))));
        body.put("contents", new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(new JSONObject().put("text", user)))));

        JSONObject res = post(url, body, new String[][]{{"x-goog-api-key", key}});
        JSONArray cands = res.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) {
            JSONObject fb = res.optJSONObject("promptFeedback");
            throw new Exception("Gemini không trả kết quả" + (fb != null ? " (" + fb.optString("blockReason") + ")" : "") + ".");
        }
        JSONObject content = cands.getJSONObject(0).optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.getJSONObject(i);
                if (part.optBoolean("thought", false)) continue;
                sb.append(part.optString("text", ""));
            }
        }
        if (sb.length() == 0) throw new Exception("Gemini trả về kết quả rỗng.");
        return sb.toString();
    }

    private static String claude(Context c, String user) throws Exception {
        String key = Prefs.claudeKey(c);
        if (key.isEmpty()) throw new Exception("Chưa nhập API key Claude. Mở app Bàn phím AI để nhập.");
        JSONObject body = new JSONObject();
        body.put("model", Prefs.claudeModel(c));
        body.put("max_tokens", 4096);
        body.put("system", SYSTEM);
        body.put("messages", new JSONArray().put(new JSONObject()
                .put("role", "user").put("content", user)));

        JSONObject res = post(new URL("https://api.anthropic.com/v1/messages"), body, new String[][]{
                {"x-api-key", key},
                {"anthropic-version", "2023-06-01"}});
        JSONArray content = res.optJSONArray("content");
        StringBuilder sb = new StringBuilder();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                JSONObject b = content.getJSONObject(i);
                if ("text".equals(b.optString("type"))) sb.append(b.optString("text"));
            }
        }
        if (sb.length() == 0) throw new Exception("Claude trả về kết quả rỗng.");
        return sb.toString();
    }

    private static JSONObject post(URL url, JSONObject body, String[][] headers) throws Exception {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        try {
            con.setRequestMethod("POST");
            con.setConnectTimeout(15000);
            con.setReadTimeout(90000);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            for (String[] h : headers) con.setRequestProperty(h[0], h[1]);
            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = con.getOutputStream()) { os.write(data); }

            int code = con.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
            String text = is == null ? "" : readAll(is);
            if (code >= 200 && code < 300) return new JSONObject(text);
            throw new Exception(friendlyError(code, text));
        } catch (java.net.UnknownHostException e) {
            throw new Exception("Không có kết nối mạng.");
        } catch (java.net.SocketTimeoutException e) {
            throw new Exception("Hết thời gian chờ, mạng chậm hoặc AI đang bận. Thử lại nhé.");
        } finally {
            con.disconnect();
        }
    }

    private static String friendlyError(int code, String text) {
        String msg = "";
        try {
            JSONObject e = new JSONObject(text).optJSONObject("error");
            if (e != null) msg = e.optString("message", "");
        } catch (Exception ignored) {}
        String hint;
        if (code == 401 || code == 403) hint = "API key sai hoặc chưa được kích hoạt";
        else if (code == 404) hint = "Tên model không đúng (sửa trong app Bàn phím AI)";
        else if (code == 429) hint = "Hết hạn mức hoặc gửi quá nhanh, đợi chút rồi thử lại";
        else if (code == 529 || code == 503) hint = "Máy chủ AI đang quá tải, thử lại sau";
        else if (msg.toLowerCase().contains("credit")) hint = "Tài khoản API Claude hết tiền (credit)";
        else hint = "Lỗi " + code;
        return msg.isEmpty() ? hint + "." : hint + ": " + msg;
    }

    private static String readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Bỏ dấu ngoặc kép hoặc khối ``` nếu AI lỡ thêm vào. */
    private static String clean(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) t = t.substring(nl + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
            t = t.trim();
        }
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("“") && t.endsWith("”")))) {
            t = t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
}
