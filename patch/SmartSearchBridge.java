package com.example.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SmartSearchBridge {

    private SmartSearchBridge() {}

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

        String normQuery =
                normalize(query == null ? "" : query);

        if (normQuery.trim().isEmpty()) {
            return true;
        }

        StringBuilder joined = new StringBuilder();

        if (targets != null) {

            for (String target : targets) {

                if (target == null) {
                    continue;
                }

                if (joined.length() > 0) {
                    joined.append(' ');
                }

                joined.append(normalize(target));
            }
        }

        String joinedTarget =
                joined.toString();

        if (joinedTarget.trim().isEmpty()) {
            return false;
        }

        if (joinedTarget.contains(normQuery)) {
            return true;
        }

        List<String> queryTokens =
                tokenize(normQuery);

        if (queryTokens.isEmpty()) {
            return false;
        }

        List<String> targetTokens =
                tokenize(joinedTarget);

        if (targetTokens.isEmpty()) {
            return false;
        }

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

    public static String normalize(String text) {

        if (text == null) {
            return "";
        }

        return text
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .trim();
    }

    private static List<String> tokenize(String text) {

        List<String> result =
                new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return result;
        }

        String normalized =
                normalize(text);

        StringBuilder token =
                new StringBuilder();

        for (int i = 0; i < normalized.length();) {

            int cp =
                    normalized.codePointAt(i);

            boolean keep =
                    Character.isLetter(cp)
                            || Character.isDigit(cp);

            if (keep) {

                token.appendCodePoint(cp);

            } else if (token.length() > 0) {

                result.add(token.toString());
                token.setLength(0);
            }

            i += Character.charCount(cp);
        }

        if (token.length() > 0) {
            result.add(token.toString());
        }

        return result;
    }

    private static boolean tokensMatch(
            String a,
            String b
    ) {

        if (a.equals(b)) {
            return true;
        }

        int minLen =
                Math.min(a.length(), b.length());

        if (minLen <= 2) {
            return false;
        }

        if (stemLikeMatch(a, b)) {
            return true;
        }

        return typoTolerant(a, b);
    }

    private static boolean stemLikeMatch(
            String a,
            String b
    ) {

        int minLen =
                Math.min(a.length(), b.length());

        int prefix =
                commonPrefixLength(a, b);

        int allowedTail;

        if (minLen <= 5) {
            allowedTail = 2;
        } else if (minLen <= 8) {
            allowedTail = 3;
        } else {
            allowedTail = 4;
        }

        return prefix >= 3
                && prefix >= minLen - allowedTail;
    }

    private static boolean typoTolerant(
            String a,
            String b
    ) {

        int minLen =
                Math.min(a.length(), b.length());

        int maxLen =
                Math.max(a.length(), b.length());

        if (minLen < 4) {
            return false;
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

        return editDistance(a, b)
                <= allowedDistance;
    }

    private static int commonPrefixLength(
            String a,
            String b
    ) {

        int n =
                Math.min(a.length(), b.length());

        int i = 0;

        while (i < n
                && a.charAt(i) == b.charAt(i)) {
            i++;
        }

        return i;
    }

    private static int editDistance(
            String a,
            String b
    ) {

        int n = a.length();
        int m = b.length();

        if (n == 0) {
            return m;
        }

        if (m == 0) {
            return n;
        }

        int[] previous =
                new int[m + 1];

        int[] current =
                new int[m + 1];

        for (int j = 0; j <= m; j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= n; i++) {

            current[0] = i;

            for (int j = 1; j <= m; j++) {

                int cost =
                        a.charAt(i - 1)
                                == b.charAt(j - 1)
                                ? 0
                                : 1;

                current[j] = Math.min(
                        Math.min(
                                previous[j] + 1,
                                current[j - 1] + 1
                        ),
                        previous[j - 1] + cost
                );
            }

            int[] tmp = previous;
            previous = current;
            current = tmp;
        }

        return previous[m];
    }
}
