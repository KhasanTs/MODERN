package com.example.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SmartSearch {
    private static final Pattern TOKEN_REGEX = Pattern.compile("[\\p{L}\\p{Nd}]+");

    private SmartSearch() {}

    public static boolean matches(String query, String target) {
        return matches(query, target, null);
    }

    public static boolean matches(String query, String target1, String target2) {
        String normQuery = normalize(query == null ? "" : query);
        if (normQuery.trim().isEmpty()) return true;

        StringBuilder joined = new StringBuilder();
        appendNormalized(joined, target1);
        appendNormalized(joined, target2);
        String joinedTarget = joined.toString().trim();

        if (joinedTarget.trim().isEmpty()) return false;
        if (joinedTarget.contains(normQuery)) return true;

        List<String> queryTokens = tokenize(normQuery);
        if (queryTokens.isEmpty()) return false;
        List<String> targetTokens = tokenize(joinedTarget);
        if (targetTokens.isEmpty()) return false;

        for (String qt : queryTokens) {
            boolean found = false;
            for (String tt : targetTokens) {
                if (tokensMatch(qt, tt)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static void appendNormalized(StringBuilder sb, String value) {
        if (value == null) return;
        String normalized = normalize(value);
        if (normalized.trim().isEmpty()) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(normalized);
    }

    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }

    private static List<String> tokenize(String text) {
        ArrayList<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        Matcher m = TOKEN_REGEX.matcher(normalize(text));
        while (m.find()) result.add(m.group());
        return result;
    }

    private static boolean tokensMatch(String a, String b) {
        if (a.equals(b)) return true;
        int minLen = Math.min(a.length(), b.length());
        if (minLen <= 2) return false;
        return stemLikeMatch(a, b) || typoTolerant(a, b);
    }

    private static boolean stemLikeMatch(String a, String b) {
        int minLen = Math.min(a.length(), b.length());
        int prefix = commonPrefixLength(a, b);
        int allowedTail = minLen <= 5 ? 2 : (minLen <= 8 ? 3 : 4);
        return prefix >= 3 && prefix >= minLen - allowedTail;
    }

    private static boolean typoTolerant(String a, String b) {
        int minLen = Math.min(a.length(), b.length());
        int maxLen = Math.max(a.length(), b.length());
        if (minLen < 4) return false;
        if (maxLen - minLen > 3) return false;
        int allowedDistance = maxLen <= 5 ? 1 : (maxLen <= 9 ? 2 : 3);
        return editDistance(a, b) <= allowedDistance;
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    private static int editDistance(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }
}
