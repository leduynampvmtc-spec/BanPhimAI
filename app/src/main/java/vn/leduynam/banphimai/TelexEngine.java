package vn.leduynam.banphimai;

import java.util.ArrayList;
import java.util.List;

/**
 * Bộ gõ Telex đơn giản cho một từ.
 * Lưu chuỗi phím thô (raw) và dựng lại chữ tiếng Việt mỗi lần gõ,
 * nên phím xóa chỉ cần bỏ phím cuối rồi dựng lại.
 * Dấu đặt theo kiểu cũ (hòa, thủy) giống mặc định của Unikey.
 */
public class TelexEngine {

    private static final String BASES = "aăâeêioôơuưy";
    private static final String[] TONES = {
            "aáàảãạ", "ăắằẳẵặ", "âấầẩẫậ", "eéèẻẽẹ", "êếềểễệ", "iíìỉĩị",
            "oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ", "uúùủũụ", "ưứừửữự", "yýỳỷỹỵ"
    };

    private static class Ch {
        char b;        // chữ thường, có thể là ă â ê ô ơ ư đ
        boolean up;    // viết hoa
        Ch(char b, boolean up) { this.b = b; this.up = up; }
    }

    private final StringBuilder raw = new StringBuilder();

    public void reset() { raw.setLength(0); }

    public boolean isEmpty() { return raw.length() == 0; }

    public void type(char c) { raw.append(c); }

    /** Xóa phím cuối. Trả về false nếu đã rỗng. */
    public boolean backspace() {
        if (raw.length() == 0) return false;
        raw.setLength(raw.length() - 1);
        return true;
    }

    public String getText() { return convert(raw.toString()); }

    // ------------------------------------------------------------------

    private static boolean isVowel(char b) { return BASES.indexOf(b) >= 0; }

    private static int toneOf(char c) {
        switch (c) {
            case 's': return 1;
            case 'f': return 2;
            case 'r': return 3;
            case 'x': return 4;
            case 'j': return 5;
            default: return -1;
        }
    }

    /** Vị trí [đầu, cuối] của cụm nguyên âm cuối cùng; null nếu không có. */
    private static int[] lastRun(List<Ch> out) {
        int end = -1;
        for (int i = out.size() - 1; i >= 0; i--) {
            if (isVowel(out.get(i).b)) { end = i; break; }
        }
        if (end < 0) return null;
        int start = end;
        while (start - 1 >= 0 && isVowel(out.get(start - 1).b)) start--;
        return new int[]{start, end};
    }

    public static String convert(String keys) {
        List<Ch> out = new ArrayList<>();
        int tone = 0;

        for (int k = 0; k < keys.length(); k++) {
            char orig = keys.charAt(k);
            char c = Character.toLowerCase(orig);
            boolean up = Character.isUpperCase(orig);
            int[] run = lastRun(out);

            // --- phím dấu thanh: s f r x j
            int t = toneOf(c);
            if (t > 0 && run != null) {
                if (tone == t) { tone = 0; out.add(new Ch(c, up)); }
                else tone = t;
                continue;
            }
            // --- z: xóa dấu thanh
            if (c == 'z' && run != null && tone != 0) { tone = 0; continue; }

            // --- dd -> đ
            if (c == 'd' && !out.isEmpty()) {
                Ch f = out.get(0);
                if (f.b == 'd') { f.b = 'đ'; continue; }
                if (f.b == 'đ') { f.b = 'd'; out.add(new Ch('d', up)); continue; }
            }

            // --- aa ee oo -> â ê ô
            if ((c == 'a' || c == 'e' || c == 'o') && run != null) {
                char hat = c == 'a' ? 'â' : (c == 'e' ? 'ê' : 'ô');
                boolean done = false;
                for (int i = run[1]; i >= run[0]; i--) {
                    Ch x = out.get(i);
                    if (x.b == hat) { x.b = c; out.add(new Ch(c, up)); done = true; break; }
                    if (x.b == c || (c == 'a' && x.b == 'ă') || (c == 'o' && x.b == 'ơ')) {
                        x.b = hat; done = true; break;
                    }
                }
                if (done) continue;
            }

            // --- w: ă ơ ư, uo -> ươ, w đứng riêng -> ư
            if (c == 'w') {
                if (run != null) {
                    // đã có dấu móc/trăng -> gỡ bỏ, gõ chữ w thường
                    boolean hasHook = false;
                    for (int i = run[0]; i <= run[1]; i++) {
                        char b = out.get(i).b;
                        if (b == 'ă' || b == 'ơ' || b == 'ư') hasHook = true;
                    }
                    // trường hợp "ưo" -> "ươ" (vd: gõ "uwo" rồi w)
                    boolean fixed = false;
                    for (int i = run[0]; i < run[1]; i++) {
                        if (out.get(i).b == 'ư' && out.get(i + 1).b == 'o') {
                            out.get(i + 1).b = 'ơ'; fixed = true; break;
                        }
                    }
                    if (fixed) continue;
                    if (hasHook) {
                        for (int i = run[0]; i <= run[1]; i++) {
                            Ch x = out.get(i);
                            if (x.b == 'ă') x.b = 'a';
                            else if (x.b == 'ơ') x.b = 'o';
                            else if (x.b == 'ư') x.b = 'u';
                        }
                        out.add(new Ch('w', up));
                        continue;
                    }
                    boolean done = false;
                    for (int i = run[0]; i < run[1]; i++) {
                        if (out.get(i).b == 'u' && (out.get(i + 1).b == 'o' || out.get(i + 1).b == 'ô')) {
                            // "qu" + o: chỉ móc chữ o (quơ)
                            if (i > 0 && out.get(i - 1).b == 'q') { out.get(i + 1).b = 'ơ'; }
                            else { out.get(i).b = 'ư'; out.get(i + 1).b = 'ơ'; }
                            done = true; break;
                        }
                    }
                    if (!done) {
                        for (int i = run[1]; i >= run[0]; i--) {
                            Ch x = out.get(i);
                            if (x.b == 'a' || x.b == 'â') { x.b = 'ă'; done = true; break; }
                            if (x.b == 'o' || x.b == 'ô') { x.b = 'ơ'; done = true; break; }
                            if (x.b == 'u') {
                                if (i > 0 && out.get(i - 1).b == 'q' && i < run[1]) continue;
                                x.b = 'ư'; done = true; break;
                            }
                        }
                    }
                    if (done) continue;
                }
                out.add(new Ch('ư', up));
                continue;
            }

            out.add(new Ch(c, up));
        }
        return render(out, tone);
    }

    private static String render(List<Ch> out, int tone) {
        // "ươ" ở cuối từ (chưa có phụ âm cuối) hiển thị là "uơ": thuở, huơ
        int n = out.size();
        if (n >= 2 && out.get(n - 2).b == 'ư' && out.get(n - 1).b == 'ơ') out.get(n - 2).b = 'u';
        int pos = -1;
        if (tone > 0) pos = tonePos(out);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            Ch x = out.get(i);
            char ch = x.b;
            if (i == pos) {
                int bi = BASES.indexOf(ch);
                if (bi >= 0) ch = TONES[bi].charAt(tone);
            }
            sb.append(x.up ? Character.toUpperCase(ch) : ch);
        }
        return sb.toString();
    }

    private static int tonePos(List<Ch> out) {
        int[] run = lastRun(out);
        if (run == null) return -1;
        int s = run[0], e = run[1];
        // "qu", "gi" + nguyên âm: u / i thuộc phụ âm đầu
        if (e > s && s > 0) {
            char prev = out.get(s - 1).b, first = out.get(s).b;
            if ((prev == 'q' && first == 'u') || (prev == 'g' && first == 'i')) s++;
        }
        // ưu tiên nguyên âm có dấu mũ / móc
        for (int i = e; i >= s; i--) {
            char b = out.get(i).b;
            if (b == 'â' || b == 'ă' || b == 'ê' || b == 'ô' || b == 'ơ') return i;
        }
        for (int i = s; i <= e; i++) if (out.get(i).b == 'ư') return i;
        int len = e - s + 1;
        if (len == 1) return s;
        if (len >= 3) return s + 1;
        boolean endsWithConsonant = e < out.size() - 1;
        return endsWithConsonant ? e : s;
    }
}
