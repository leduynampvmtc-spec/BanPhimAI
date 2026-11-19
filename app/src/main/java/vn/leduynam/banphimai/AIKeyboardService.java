package vn.leduynam.banphimai;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AIKeyboardService extends InputMethodService {

    // ---- Các nút AI: nhãn + yêu cầu gửi cho AI
    private static final String[][] ACTIONS = {
            {"✓ Sửa lỗi", "Sửa lỗi chính tả, ngữ pháp và dấu câu. Nếu là tiếng Việt viết thiếu dấu thì thêm dấu cho đúng. Giữ nguyên ngôn ngữ, ý và giọng văn, không thêm nội dung mới."},
            {"Lịch sự", "Viết lại cho lịch sự, trang trọng, phù hợp gửi cấp trên, đồng nghiệp hoặc phụ huynh. Giữ nguyên ngôn ngữ và đủ ý."},
            {"Thân thiện", "Viết lại cho thân thiện, tự nhiên, gần gũi. Giữ nguyên ngôn ngữ và đủ ý."},
            {"Ngắn gọn", "Rút gọn cho ngắn gọn, rõ ý, giữ các thông tin quan trọng. Giữ nguyên ngôn ngữ."},
            {"Mở rộng", "Viết lại thành đoạn văn rõ ràng, đầy đủ, mạch lạc hơn từ các ý đã có. Không bịa thêm thông tin cụ thể. Giữ nguyên ngôn ngữ."},
            {"→ Anh", "Dịch sang tiếng Anh tự nhiên, đúng ngữ cảnh."},
            {"→ Việt", "Dịch sang tiếng Việt tự nhiên, đúng ngữ cảnh."},
    };

    private static final String[][] LETTERS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL"},
    };
    private static final String[][] SYM1 = {
            {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"},
            {"@", "#", "đ", "%", "&", "-", "+", "(", ")", "/"},
            {"SYM2", "*", "\"", "'", ":", ";", "!", "?", "DEL"},
    };
    private static final String[][] SYM2 = {
            {"~", "`", "|", "•", "√", "π", "÷", "×", "°", "€"},
            {"$", "£", "¥", "^", "=", "{", "}", "[", "]", "\\"},
            {"SYM1", "<", ">", "_", "©", "✓", "…", "₫", "DEL"},
    };

    private final TelexEngine engine = new TelexEngine();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout root, keysBox;
    private LinearLayout chips;
    private TextView status, providerChip, langKey;
    private HorizontalScrollView chipScroll;

    private int shift = 0;          // 0 thường, 1 hoa một lần, 2 khóa hoa
    private long lastShiftTap = 0;
    private int layer = 0;          // 0 chữ, 1 số, 2 ký hiệu
    private boolean telexAllowed = true;
    private boolean aiAllowed = true;
    private boolean busy = false;

    private int selStart = 0, selEnd = 0;

    // hoàn tác
    private String undoOriginal = null;
    private boolean undoWhole;
    private int undoStart, undoLen;

    // màu
    private int cBg, cKey, cKeyFn, cText, cAccent, cChip;

    // ================================================================ vòng đời

    @Override
    public View onCreateInputView() {
        pickColors();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setPadding(dp(3), dp(4), dp(3), dp(6));

        root.addView(buildToolbar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(keysBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildKeys();
        return root;
    }

    @Override
    public void onStartInput(EditorInfo attr, boolean restarting) {
        super.onStartInput(attr, restarting);
        engine.reset();
        busy = false;
        int cls = attr.inputType & InputType.TYPE_MASK_CLASS;
        int var = attr.inputType & InputType.TYPE_MASK_VARIATION;
        boolean password = cls == InputType.TYPE_CLASS_TEXT && (
                var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        boolean techy = cls == InputType.TYPE_CLASS_TEXT && (
                var == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || var == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                        || var == InputType.TYPE_TEXT_VARIATION_URI);
        boolean numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE
                || cls == InputType.TYPE_CLASS_DATETIME;
        telexAllowed = !password && !techy && !numeric;
        aiAllowed = !password && !numeric;
        layer = numeric ? 1 : 0;
        shift = 0;
        updateAutoCaps();
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        pickColors();
        if (root != null) {
            root.setBackgroundColor(cBg);
            refreshToolbar();
            buildKeys();
        }
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        engine.reset();
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                                  int candStart, int candEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candStart, candEnd);
        selStart = newSelStart;
        selEnd = newSelEnd;
        if (!engine.isEmpty() && (candStart < 0 || newSelStart != candEnd || newSelEnd != candEnd)) {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.finishComposingText();
            engine.reset();
        }
    }

    // ================================================================ giao diện

    private void pickColors() {
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            cBg = 0xFF1E1F22; cKey = 0xFF3A3B40; cKeyFn = 0xFF56575D; cText = 0xFFECECEF;
            cAccent = 0xFFA78BFA; cChip = 0xFF2E2A45;
        } else {
            cBg = 0xFFE6E8EE; cKey = 0xFFFFFFFF; cKeyFn = 0xFFC8CCD6; cText = 0xFF1F2024;
            cAccent = 0xFF6D4AE8; cChip = 0xFFEDE7FF;
        }
    }

    private View buildToolbar() {
        FrameLayout bar = new FrameLayout(this);

        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chipScroll.addView(chips);
        bar.addView(chipScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        providerChip = chip("", true);
        providerChip.setOnClickListener(v -> {
            String p = Prefs.CLAUDE.equals(Prefs.provider(this)) ? Prefs.GEMINI : Prefs.CLAUDE;
            Prefs.setProvider(this, p);
            refreshToolbar();
            flash("Đã chuyển sang " + Prefs.providerName(p), false);
        });
        chips.addView(providerChip);

        for (String[] a : ACTIONS) {
            TextView t = chip(a[0], false);
            t.setOnClickListener(v -> runAi(a[1]));
            chips.addView(t);
        }
        TextView undo = chip("↶ Hoàn tác", false);
        undo.setOnClickListener(v -> undoAi());
        chips.addView(undo);

        TextView gear = chip("⚙", false);
        gear.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        });
        chips.addView(gear);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12), 0, dp(12), 0);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setVisibility(View.GONE);
        status.setOnClickListener(v -> { if (!busy) status.setVisibility(View.GONE); });
        bar.addView(status, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        refreshToolbar();
        return bar;
    }

    private TextView chip(String label, boolean strong) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), 0, dp(12), 0);
        t.setSingleLine(true);
        if (strong) t.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        lp.setMargins(dp(3), 0, dp(3), 0);
        t.setLayoutParams(lp);
        return t;
    }

    private void refreshToolbar() {
        if (chips == null) return;
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView t = (TextView) chips.getChildAt(i);
            boolean prov = t == providerChip;
            t.setBackground(round(prov ? cAccent : cChip, dp(17)));
            t.setTextColor(prov ? Color.WHITE : cAccent);
            t.setAlpha(aiAllowed || prov ? 1f : 0.4f);
        }
        providerChip.setText(Prefs.providerName(Prefs.provider(this)) + " ⇄");
        if (status != null) status.setBackground(round(cChip, dp(10)));
    }

    private void buildKeys() {
        if (keysBox == null) return;
        keysBox.removeAllViews();
        String[][] rows = layer == 0 ? LETTERS : (layer == 1 ? SYM1 : SYM2);
        for (int r = 0; r < rows.length; r++) {
            LinearLayout row = newRow();
            if (layer == 0 && r == 1) row.addView(spacer(0.5f));
            for (String k : rows[r]) {
                float w = (k.equals("SHIFT") || k.equals("DEL") || k.startsWith("SYM")) ? 1.5f : 1f;
                row.addView(key(k, w));
            }
            if (layer == 0 && r == 1) row.addView(spacer(0.5f));
            keysBox.addView(row);
        }
        LinearLayout last = newRow();
        last.addView(key(layer == 0 ? "123" : "ABC", 1.5f));
        langKey = key("LANG", 1.2f);
        last.addView(langKey);
        last.addView(key(",", 1f));
        last.addView(key("SPACE", 4.3f));
        last.addView(key(".", 1f));
        last.addView(key("ENTER", 1.8f));
        keysBox.addView(last);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(land ? 40 : 52)));
        return row;
    }

    private View spacer(float w) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w));
        return v;
    }

    private TextView key(String code, float weight) {
        TextView t = new TextView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(dp(3), dp(4), dp(3), dp(4));
        t.setLayoutParams(lp);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(cText);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        boolean fn = true;
        switch (code) {
            case "SHIFT": t.setText(shift == 2 ? "⇪" : "⇧"); break;
            case "DEL": t.setText("⌫"); break;
            case "SYM1": t.setText("123"); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); break;
            case "SYM2": t.setText("=\\<"); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); break;
            case "123": case "ABC": t.setText(code); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); break;
            case "LANG": t.setText(telexOn() ? "VI" : "EN"); t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                if (telexOn()) t.setTextColor(cAccent);
                t.setTypeface(Typeface.DEFAULT_BOLD); break;
            case "ENTER": t.setText("⏎"); break;
            case "SPACE": t.setText(telexOn() ? "Tiếng Việt" : "English");
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13); t.setAlpha(0.75f); fn = false; break;
            default:
                fn = false;
                t.setText(shift > 0 && layer == 0 ? code.toUpperCase() : code);
        }
        int bg = fn ? cKeyFn : cKey;
        if (code.equals("SHIFT") && shift > 0) bg = cAccent;
        if (code.equals("SHIFT") && shift > 0) t.setTextColor(Color.WHITE);
        if (code.equals("ENTER")) { bg = cAccent; t.setTextColor(Color.WHITE); }
        final int normal = bg;
        t.setBackground(round(normal, dp(7)));
        t.setOnTouchListener(new KeyTouch(code, t, normal));
        return t;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Xử lý chạm: phản hồi ngay khi nhấn, giữ phím xóa để xóa liên tục, giữ phím cách để đổi bàn phím. */
    private class KeyTouch implements View.OnTouchListener {
        final String code; final TextView view; final int color;
        boolean longFired;
        final Runnable repeat = new Runnable() {
            @Override public void run() { onDelete(); ui.postDelayed(this, 55); }
        };
        final Runnable spaceLong = () -> {
            longFired = true;
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        };

        KeyTouch(String code, TextView view, int color) { this.code = code; this.view = view; this.color = color; }

        @Override public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setBackground(round(code.equals("ENTER") ? cAccent : cKeyFn, dp(7)));
                    if (!code.equals("ENTER")) view.setAlpha(0.85f);
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    longFired = false;
                    if (code.equals("DEL")) { onDelete(); ui.postDelayed(repeat, 420); }
                    else if (code.equals("SPACE")) ui.postDelayed(spaceLong, 550);
                    else onKey(code);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(repeat);
                    ui.removeCallbacks(spaceLong);
                    view.setBackground(round(color, dp(7)));
                    view.setAlpha(code.equals("SPACE") ? 0.75f : 1f);
                    if (code.equals("SPACE") && !longFired && e.getActionMasked() == MotionEvent.ACTION_UP) onSpace();
                    return true;
            }
            return false;
        }
    }

    // ================================================================ gõ phím

    private boolean telexOn() { return telexAllowed && Prefs.telex(this); }

    private void onKey(String code) {
        switch (code) {
            case "SHIFT": {
                long now = System.currentTimeMillis();
                if (shift == 0) shift = 1;
                else if (shift == 1 && now - lastShiftTap < 350) shift = 2;
                else shift = 0;
                lastShiftTap = now;
                buildKeys();
                return;
            }
            case "123": case "SYM1": layer = 1; buildKeys(); return;
            case "SYM2": layer = 2; buildKeys(); return;
            case "ABC": layer = 0; buildKeys(); return;
            case "LANG":
                commitComposing();
                Prefs.setTelex(this, !Prefs.telex(this));
                buildKeys();
                return;
            case "ENTER": onEnter(); return;
            default:
                char c = code.charAt(0);
                if (shift > 0 && layer == 0) c = Character.toUpperCase(c);
                typeChar(c);
                if (shift == 1 && layer == 0) { shift = 0; buildKeys(); }
        }
    }

    private void typeChar(char c) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        boolean ascii = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        if (ascii && telexOn()) {
            engine.type(c);
            ic.setComposingText(engine.getText(), 1);
        } else {
            commitComposing();
            ic.commitText(String.valueOf(c), 1);
        }
    }

    private void onSpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing();
        ic.commitText(" ", 1);
        updateAutoCaps();
    }

    private void onDelete() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (!engine.isEmpty()) {
            engine.backspace();
            if (engine.isEmpty()) {
                ic.commitText("", 1);
            } else {
                ic.setComposingText(engine.getText(), 1);
            }
            return;
        }
        CharSequence sel = ic.getSelectedText(0);
        if (sel != null && sel.length() > 0) ic.commitText("", 1);
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    private void onEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing();
        EditorInfo ei = getCurrentInputEditorInfo();
        int action = ei == null ? EditorInfo.IME_ACTION_NONE : (ei.imeOptions & EditorInfo.IME_MASK_ACTION);
        boolean noAction = ei == null || (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
                || (ei.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (noAction) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            updateAutoCaps();
        } else {
            ic.performEditorAction(action);
        }
    }

    private void commitComposing() {
        if (engine.isEmpty()) return;
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.finishComposingText();
        engine.reset();
    }

    private void updateAutoCaps() {
        if (layer != 0 || shift == 2) return;
        InputConnection ic = getCurrentInputConnection();
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ic == null || ei == null || ei.inputType == InputType.TYPE_NULL) return;
        int caps = ic.getCursorCapsMode(ei.inputType);
        int want = caps != 0 ? 1 : 0;
        if (want != shift) { shift = want; buildKeys(); }
    }

    // ================================================================ AI

    private void runAi(String instruction) {
        if (busy) return;
        if (!aiAllowed) { flash("Không dùng AI trong ô mật khẩu / ô số.", true); return; }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing();

        CharSequence sel = ic.getSelectedText(0);
        final boolean whole;
        final String src;
        final int start, end;
        if (sel != null && sel.length() > 0) {
            whole = false;
            src = sel.toString();
            start = Math.min(selStart, selEnd);
            end = Math.max(selStart, selEnd);
        } else {
            CharSequence b = ic.getTextBeforeCursor(20000, 0);
            CharSequence a = ic.getTextAfterCursor(20000, 0);
            whole = true;
            src = (b == null ? "" : b.toString()) + (a == null ? "" : a.toString());
            start = 0; end = 0;
        }
        if (src.trim().isEmpty()) { flash("Hãy gõ (hoặc bôi đen) đoạn văn trước rồi bấm nút AI.", true); return; }

        String prov = Prefs.provider(this);
        String key = Prefs.CLAUDE.equals(prov) ? Prefs.claudeKey(this) : Prefs.geminiKey(this);
        if (key.isEmpty()) { flash("Chưa có API key " + Prefs.providerName(prov) + ". Bấm ⚙ để nhập.", true); return; }

        busy = true;
        showStatus("⏳ " + Prefs.providerName(prov) + " đang xử lý…", false);
        final Context ctx = getApplicationContext();
        new Thread(() -> {
            String result = null, error = null;
            try { result = AiClient.run(ctx, instruction, src); }
            catch (Exception e) { error = e.getMessage() == null ? e.toString() : e.getMessage(); }
            final String r = result, err = error;
            ui.post(() -> {
                busy = false;
                if (err != null) { flash("⚠ " + err, true, 6000); return; }
                InputConnection c2 = getCurrentInputConnection();
                if (c2 == null) { status.setVisibility(View.GONE); return; }
                replace(c2, whole, start, end, r);
                undoOriginal = src; undoWhole = whole; undoStart = start; undoLen = r.length();
                flash("✓ Xong. Chưa ưng thì bấm ↶ Hoàn tác.", false, 2200);
            });
        }).start();
    }

    private void replace(InputConnection ic, boolean whole, int start, int end, String text) {
        ic.beginBatchEdit();
        if (whole) {
            CharSequence b = ic.getTextBeforeCursor(20000, 0);
            CharSequence a = ic.getTextAfterCursor(20000, 0);
            ic.deleteSurroundingText(b == null ? 0 : b.length(), a == null ? 0 : a.length());
        } else {
            ic.setSelection(start, end);
        }
        ic.commitText(text, 1);
        ic.endBatchEdit();
    }

    private void undoAi() {
        if (busy) return;
        if (undoOriginal == null) { flash("Chưa có gì để hoàn tác.", false); return; }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        commitComposing();
        replace(ic, undoWhole, undoStart, undoStart + undoLen, undoOriginal);
        undoOriginal = null;
        flash("Đã trả lại đoạn văn ban đầu.", false);
    }

    // ================================================================ thông báo trên thanh AI

    private final Runnable hideStatus = () -> { if (!busy && status != null) status.setVisibility(View.GONE); };

    private void showStatus(String msg, boolean error) {
        if (status == null) return;
        ui.removeCallbacks(hideStatus);
        status.setText(msg);
        status.setTextColor(error ? 0xFFD93025 : cAccent);
        status.setVisibility(View.VISIBLE);
    }

    private void flash(String msg, boolean error) { flash(msg, error, error ? 3500 : 1800); }

    private void flash(String msg, boolean error, int ms) {
        showStatus(msg, error);
        ui.postDelayed(hideStatus, ms);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
