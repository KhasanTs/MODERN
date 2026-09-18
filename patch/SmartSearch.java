package com.example.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class SmartSearchBridge {

    private static final Pattern TOKEN_REGEX =
            Pattern.compile("[\\p{L}\\p{Nd}]+");

    private static final String EN_LETTERS =
            "qwertyuiopasdfghjklzxcvbnm";

    private static final String RU_LETTERS =
            "йцукенгшщзфывапролдячсмить";

    private SmartSearchBridge() {
    }

    public static boolean containsSmart(
            CharSequence haystack,
            CharSequence needle
    ) {
        return containsSmart(haystack, needle, true);
    }

    public static boolean containsSmart(
            CharSequence haystack,
            CharSequence needle,
            boolean ignoreCase
    ) {
        if (haystack == null || needle == null) {
            return false;
        }

        String hay = haystack.toString();
        String query = needle.toString();

        if (!ignoreCase) {
            return hay.contains(query);
        }

        return matches(query, hay);
    }

    public static boolean matches(
            String query,
            String... targets
    ) {
        String normQuery = normalize(query);
        if (normQuery.isEmpty()) {
            return true;
        }

        String joinedTarget = joinTargets(targets);
        if (joinedTarget.isEmpty()) {
            return false;
        }

        if (joinedTarget.contains(normQuery)) {
            return true;
        }

        String compactQuery = compact(normQuery);
        String compactTarget = compact(joinedTarget);

        if (compactQuery.length() >= 4
                && compactTarget.contains(compactQuery)) {
            return true;
        }

        List<String> variants = new ArrayList<>();
        addUnique(variants, normQuery);

        if (looksLikeKeyboardLayoutMistake(normQuery)) {
            String swapped = keyboardLayoutSwap(normQuery);
            if (!swapped.equals(normQuery) && !swapped.isEmpty()) {
                addUnique(variants, swapped);

                if (compact(swapped).length() >= 4
                        && compactTarget.contains(compact(swapped))) {
                    return true;
                }
            }
        }

        List<String> targetTokens = tokenize(joinedTarget);
        if (targetTokens.isEmpty()) {
            return false;
        }

        for (String variant : variants) {
            List<String> queryTokens = uniqueTokens(tokenize(variant));
            if (queryTokens.isEmpty()) {
                continue;
            }

            if (allQueryTokensMatch(queryTokens, targetTokens)) {
                return true;
            }
        }

        return false;
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }

        String value = text
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е');

        value = Normalizer.normalize(
                value,
                Normalizer.Form.NFKD
        );

        StringBuilder result = new StringBuilder(value.length());
        boolean pendingSpace = false;

        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);

            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }

            if (Character.isWhitespace(cp)
                    || Character.isISOControl(cp)) {
                pendingSpace = true;
                continue;
            }

            if (pendingSpace && result.length() > 0) {
                result.append(' ');
            }
            pendingSpace = false;
            result.appendCodePoint(cp);
        }

        return result.toString().trim();
    }

    private static String joinTargets(String... targets) {
        StringBuilder joined = new StringBuilder();

        if (targets == null) {
            return "";
        }

        for (String target : targets) {
            if (target == null) {
                continue;
            }

            String normalized = normalize(target);
            if (normalized.isEmpty()) {
                continue;
            }

            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(normalized);
        }

        return joined.toString();
    }

    private static List<String> tokenize(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }

        java.util.regex.Matcher matcher = TOKEN_REGEX.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private static List<String> uniqueTokens(List<String> tokens) {
        Set<String> unique = new LinkedHashSet<>(tokens);
        return new ArrayList<>(unique);
    }

    private static boolean allQueryTokensMatch(
            List<String> queryTokens,
            List<String> targetTokens
    ) {
        for (String queryToken : queryTokens) {
            boolean found = false;

            for (String targetToken : targetTokens) {
                if (tokensMatch(queryToken, targetToken)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    private static boolean tokensMatch(String a, String b) {
        if (a.equals(b)) {
            return true;
        }

        int minLen = Math.min(a.length(), b.length());
        int maxLen = Math.max(a.length(), b.length());

        if (minLen <= 3) {
            return false;
        }

        if (stemLikeMatch(a, b)) {
            return true;
        }

        if (maxLen - minLen > 3) {
            return false;
        }

        int allowedDistance;
        if (maxLen <= 5) {
            allowedDistance = 1;
        } else if (maxLen <= 9) {
            allowedDistance = 2;
        } else {
            allowedDistance = 3;
        }

        return damerauLevenshtein(a, b, allowedDistance) <= allowedDistance;
    }

    private static boolean stemLikeMatch(String a, String b) {
        int minLen = Math.min(a.length(), b.length());
        int maxLen = Math.max(a.length(), b.length());
        int prefix = commonPrefixLength(a, b);

        if (prefix < 3) {
            return false;
        }

        int allowedTail;
        if (minLen <= 5) {
            allowedTail = 2;
        } else if (minLen <= 8) {
            allowedTail = 3;
        } else {
            allowedTail = 4;
        }

        return maxLen - prefix <= allowedTail;
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;

        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }

        return i;
    }

    private static String compact(String text) {
        StringBuilder result = new StringBuilder(text.length());

        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (Character.isLetter(cp) || Character.isDigit(cp)) {
                result.appendCodePoint(cp);
            }
        }

        return result.toString();
    }

    private static boolean looksLikeKeyboardLayoutMistake(String text) {
        int latin = 0;
        int cyrillic = 0;
        int letters = 0;

        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);

            if (!Character.isLetter(cp)) {
                continue;
            }

            letters++;
            if ((cp >= 'a' && cp <= 'z')) {
                latin++;
            } else if (isCyrillic(cp)) {
                cyrillic++;
            }
        }

        if (letters < 2) {
            return false;
        }

        return latin * 10 >= letters * 7
                || cyrillic * 10 >= letters * 7;
    }

    private static boolean isCyrillic(int cp) {
        return (cp >= 'а' && cp <= 'я')
                || cp == 'ё';
    }

    private static String keyboardLayoutSwap(String text) {
        StringBuilder result = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char original = text.charAt(i);
            char lower = Character.toLowerCase(original);
            char mapped = mapLatinToRussian(lower);

            if (mapped != 0) {
                result.append(mapped);
                continue;
            }

            mapped = mapRussianToLatin(lower);
            if (mapped != 0) {
                result.append(mapped);
                continue;
            }

            result.append(original);
        }

        return normalize(result.toString());
    }

    private static char mapLatinToRussian(char c) {
        int index = EN_LETTERS.indexOf(c);
        if (index >= 0) {
            return RU_LETTERS.charAt(index);
        }

        switch (c) {
            case '[':
                return 'х';
            case ']':
                return 'ъ';
            case ';':
                return 'ж';
            case '\'':
                return 'э';
            case ',':
                return 'б';
            case '.':
                return 'ю';
            case '/':
                return '.';
            case '`':
                return 'ё';
            default:
                return 0;
        }
    }

    private static char mapRussianToLatin(char c) {
        int index = RU_LETTERS.indexOf(c);
        if (index >= 0) {
            return EN_LETTERS.charAt(index);
        }

        switch (c) {
            case 'х':
                return '[';
            case 'ъ':
                return ']';
            case 'ж':
                return ';';
            case 'э':
                return '\'';
            case 'б':
                return ',';
            case 'ю':
                return '.';
            case 'ё':
                return '`';
            default:
                return 0;
        }
    }

    private static void addUnique(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static int damerauLevenshtein(
            String a,
            String b,
            int maxDistance
    ) {
        int n = a.length();
        int m = b.length();

        if (Math.abs(n - m) > maxDistance) {
            return maxDistance + 1;
        }

        int[] prevPrev = new int[m + 1];
        int[] prev = new int[m + 1];
        int[] current = new int[m + 1];

        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            current[0] = i;

            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1)
                        ? 0
                        : 1;

                int best = Math.min(
                        Math.min(
                                prev[j] + 1,
                                current[j - 1] + 1
                        ),
                        prev[j - 1] + cost
                );

                if (i > 1
                        && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(
                            best,
                            prevPrev[j - 2] + 1
                    );
                }

                current[j] = best;
            }

            int[] swap = prevPrev;
            prevPrev = prev;
            prev = current;
            current = swap;
        }

        return prev[m];
    }
}
