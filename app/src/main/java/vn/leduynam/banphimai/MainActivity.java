package vn.leduynam.banphimai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Màn hình cài đặt: bật bàn phím, chọn AI, nhập API key, gõ thử. */
public class MainActivity extends Activity {

    private LinearLayout box;
    private TextView enabledInfo, testResult;
    private EditText gKey, gModel, cKey, cModel;
    private RadioButton rGemini, rClaude;
    private int cText, cSub, cCard, cAccent;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        cText = dark ? 0xFFECECEF : 0xFF1F2024;
        cSub = dark ? 0xFFA8A9B0 : 0xFF5F6368;
        cCard = dark ? 0xFF2A2B30 : 0xFFFFFFFF;
        cAccent = dark ? 0xFFA78BFA : 0xFF6D4AE8;
        int bg = dark ? 0xFF18191C : 0xFFF3F1FA;

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(bg);
        sv.setFitsSystemWindows(true);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(20), dp(16), dp(32));
        sv.addView(box);
        setContentView(sv);

        TextView title = text("Bàn phím AI", 28, cAccent, true);
        box.addView(title);
        box.addView(text("Gõ Telex tiếng Việt + nút AI sửa lỗi, viết lại, dịch bằng Gemini hoặc Claude.", 15, cSub, false));

        // ---- Bước 1, 2
        LinearLayout c1 = card("Bước 1 – Bật bàn phím");
        enabledInfo = text("", 14, cSub, false);
        c1.addView(enabledInfo);
        c1.addView(button("Mở cài đặt bàn phím", v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        c1.addView(text("Bật công tắc \"Bàn phím AI\" rồi bấm OK ở các thông báo của Android "
                + "(thông báo nói bàn phím có thể đọc chữ bạn gõ — đây là cảnh báo chung cho mọi bàn phím).", 13, cSub, false));

        LinearLayout c2 = card("Bước 2 – Chọn làm bàn phím đang dùng");
        c2.addView(button("Chọn bàn phím", v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }));
        c2.addView(text("Khi đang gõ, giữ lâu phím cách để đổi qua lại với bàn phím khác.", 13, cSub, false));

        // ---- AI
        LinearLayout c3 = card("Bước 3 – Chọn AI và nhập API key");
        RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rGemini = radio("Gemini");
        rClaude = radio("Claude");
        rg.addView(rGemini);
        rg.addView(rClaude);
        c3.addView(rg);

        c3.addView(text("Gemini API key", 15, cText, true));
        gKey = field("Dán key bắt đầu bằng AIza…", true);
        c3.addView(gKey);
        c3.addView(link("Lấy key Gemini (có gói miễn phí) →", "https://aistudio.google.com/apikey"));
        gModel = field("Model Gemini (mặc định " + Prefs.DEFAULT_GEMINI_MODEL + ")", false);
        c3.addView(gModel);

        c3.addView(text("Claude API key", 15, cText, true));
        cKey = field("Dán key bắt đầu bằng sk-ant-…", true);
        c3.addView(cKey);
        c3.addView(link("Lấy key Claude →", "https://console.anthropic.com/settings/keys"));
        c3.addView(text("Lưu ý: API Claude tính tiền riêng theo lượng dùng (nạp credit ở console.anthropic.com), "
                + "không dùng chung gói Claude Pro trên claude.ai.", 13, cSub, false));
        cModel = field("Model Claude (mặc định " + Prefs.DEFAULT_CLAUDE_MODEL + ")", false);
        c3.addView(cModel);

        c3.addView(button("Lưu", v -> { save(); toast("Đã lưu"); }));
        c3.addView(button("Lưu và thử kết nối", v -> testAi()));
        testResult = text("", 14, cSub, false);
        c3.addView(testResult);

        // ---- Gõ thử
        LinearLayout c4 = card("Gõ thử");
        Switch telex = new Switch(this);
        telex.setText("Gõ tiếng Việt kiểu Telex (phím VI/EN trên bàn phím)");
        telex.setTextColor(cText);
        telex.setChecked(Prefs.telex(this));
        telex.setOnCheckedChangeListener((x, on) -> Prefs.setTelex(this, on));
        c4.addView(telex);
        EditText test = new EditText(this);
        test.setHint("Gõ thử ở đây, ví dụ: chao moi nguoi, toi ten la Nam");
        test.setMinLines(3);
        test.setGravity(Gravity.TOP);
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        test.setTextColor(cText);
        test.setHintTextColor(cSub);
        c4.addView(test);
        c4.addView(text("Cách dùng: gõ đoạn văn → bấm nút trên thanh tím (Sửa lỗi, Lịch sự, → Anh…). "
                + "Nếu bôi đen một phần thì AI chỉ sửa phần đó. Bấm \"Gemini ⇄\" để đổi sang Claude.", 13, cSub, false));

        // ---- Tác giả
        LinearLayout c5 = card("Người viết ứng dụng");
        c5.addView(text("Lê Duy Nam", 16, cText, true));
        c5.addView(text("Giảng viên Trường Cao đẳng Dầu khí", 14, cSub, false));
        c5.addView(link("Mobile: 0946 789 911", "tel:0946789911"));

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isEnabled();
        enabledInfo.setText(on ? "✅ Bàn phím AI đã được bật." : "❌ Bàn phím AI chưa được bật.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private boolean isEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        for (InputMethodInfo i : imm.getEnabledInputMethodList()) {
            if (getPackageName().equals(i.getPackageName())) return true;
        }
        return false;
    }

    private void load() {
        boolean claude = Prefs.CLAUDE.equals(Prefs.provider(this));
        rGemini.setChecked(!claude);
        rClaude.setChecked(claude);
        gKey.setText(Prefs.geminiKey(this));
        cKey.setText(Prefs.claudeKey(this));
        gModel.setText(Prefs.sp(this).getString("gemini_model", ""));
        cModel.setText(Prefs.sp(this).getString("claude_model", ""));
    }

    private void save() {
        Prefs.sp(this).edit()
                .putString("provider", rClaude.isChecked() ? Prefs.CLAUDE : Prefs.GEMINI)
                .putString("gemini_key", gKey.getText().toString().trim())
                .putString("claude_key", cKey.getText().toString().trim())
                .putString("gemini_model", gModel.getText().toString().trim())
                .putString("claude_model", cModel.getText().toString().trim())
                .apply();
    }

    private void testAi() {
        save();
        String name = Prefs.providerName(Prefs.provider(this));
        testResult.setText("⏳ Đang thử " + name + "…");
        final Context ctx = getApplicationContext();
        new Thread(() -> {
            String msg;
            try {
                String r = AiClient.run(ctx,
                        "Sửa lỗi chính tả và thêm dấu tiếng Việt.", "xin chao, toi la giang vien truong cao dang dau khi");
                msg = "✅ " + name + " hoạt động. Kết quả: " + r;
            } catch (Exception e) {
                msg = "⚠ " + e.getMessage();
            }
            final String m = msg;
            runOnUiThread(() -> testResult.setText(m));
        }).start();
    }

    // ---------------------------------------------------------------- tiện ích giao diện

    private LinearLayout card(String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable d = new GradientDrawable();
        d.setColor(cCard);
        d.setCornerRadius(dp(16));
        c.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        box.addView(c, lp);
        c.addView(text(heading, 18, cText, true));
        return c;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(0xFFFFFFFF);
        GradientDrawable d = new GradientDrawable();
        d.setColor(cAccent);
        d.setCornerRadius(dp(24));
        btn.setBackground(d);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private EditText field(String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextColor(cText);
        e.setHintTextColor(cSub);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (secret) e.setTypeface(Typeface.MONOSPACE);
        return e;
    }

    private RadioButton radio(String label) {
        RadioButton r = new RadioButton(this);
        r.setText(label);
        r.setTextColor(cText);
        r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        r.setId(View.generateViewId());
        r.setPadding(0, 0, dp(24), 0);
        return r;
    }

    private TextView link(String label, String url) {
        TextView t = text(label, 14, cAccent, true);
        t.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { toast("Không mở được liên kết"); }
        });
        return t;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
