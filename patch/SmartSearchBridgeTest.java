import com.example.utils.SmartSearchBridge;

public final class TestSmartSearch {
    private static int passed = 0;
    private static int failed = 0;

    private static void expect(boolean actual, String name) {
        if (actual) {
            passed++;
            System.out.println("PASS: " + name);
        } else {
            failed++;
            System.out.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        expect(SmartSearchBridge.containsSmart("еловек-паук", "человек паук"), "hyphen + spaces");
        expect(SmartSearchBridge.containsSmart("еловек-паук", "человекпаук"), "compact query");
        expect(SmartSearchBridge.containsSmart("еловек-паук", "человк паук"), "missing letter");
        expect(SmartSearchBridge.containsSmart("лка в лесу", "елка"), "yo-e normalization");
        expect(SmartSearchBridge.containsSmart("Hello World", "hello"), "case insensitive");
        expect(SmartSearchBridge.containsSmart("Star Wars Episode IV", "wars star"), "token order");
        expect(SmartSearchBridge.containsSmart("ривет мир", "ghbdtn"), "keyboard layout");
        expect(SmartSearchBridge.containsSmart("Hello мир", "руддщ"), "reverse keyboard layout");
        expect(SmartSearchBridge.containsSmart("Café", "cafe"), "accent folding");
        expect(SmartSearchBridge.containsSmart("Spider-Man", "spider man"), "punctuation split");
        expect(SmartSearchBridge.containsSmart("Spider-Man", "spiderman"), "punctuation compact");
        expect(!SmartSearchBridge.containsSmart("ир", "мы"), "short word false positive");
        expect(!SmartSearchBridge.containsSmart("от", "катастрофа"), "short word protection");
        expect(!SmartSearchBridge.containsSmart("атрица", "матрицадругая"), "different word protection");
        expect(SmartSearchBridge.matches("дом", "ольшой дом", "анал о кино"), "multiple targets");
        expect(SmartSearchBridge.matches("фильмы", "учшие фильмы 2026"), "word form");
        expect(!SmartSearchBridge.matches("привет", (String)null), "null target");

        System.out.println("RESULT: passed=" + passed + " failed=" + failed);

        if (failed != 0) {
            throw new AssertionError("SmartSearch tests failed: " + failed);
        }
    }
}
