package vn.leduynam.banphimai;

import android.content.Context;
import android.content.SharedPreferences;

/** Cài đặt lưu trên máy (API key chỉ nằm trong điện thoại, không gửi đi đâu khác). */
public final class Prefs {
    public static final String GEMINI = "gemini";
    public static final String CLAUDE = "claude";

    public static final String DEFAULT_GEMINI_MODEL = "gemini-flash-latest";
    public static final String DEFAULT_CLAUDE_MODEL = "claude-haiku-5-5";

    private Prefs() {}

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    public static String provider(Context c) { return sp(c).getString("provider", GEMINI); }
    public static void setProvider(Context c, String p) { sp(c).edit().putString("provider", p).apply(); }

    public static String geminiKey(Context c) { return sp(c).getString("gemini_key", "").trim(); }
    public static String claudeKey(Context c) { return sp(c).getString("claude_key", "").trim(); }

    public static String geminiModel(Context c) {
        String m = sp(c).getString("gemini_model", "").trim();
        return m.isEmpty() ? DEFAULT_GEMINI_MODEL : m;
    }
    public static String claudeModel(Context c) {
        String m = sp(c).getString("claude_model", "").trim();
        return m.isEmpty() ? DEFAULT_CLAUDE_MODEL : m;
    }

    public static boolean telex(Context c) { return sp(c).getBoolean("telex", true); }
    public static void setTelex(Context c, boolean on) { sp(c).edit().putBoolean("telex", on).apply(); }

    public static String providerName(String p) { return CLAUDE.equals(p) ? "Claude" : "Gemini"; }
}
