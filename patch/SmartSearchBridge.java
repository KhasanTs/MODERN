package com.example.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SmartSearchBridge {

    private static final Pattern TOKEN_REGEX =
            Pattern.compile("[\\p{L}\\p{Nd}]+");

    /*
     * Keyboard layout mappings:
     *
     * English:
     * qwertyuiop
     * asdfghjkl
     * zxcvbnm
     *
     * Russian:
     * йцукенгшщз
     * фывапролд
     * ячсмить
     */
    private static final String EN_LETTERS =
            "qwertyuiopasdfghjklzxcvbnm";

    private static final String RU_LETTERS =
            "йцукенгшщзфывапролъдячсмить"
                    .replace("лъ", "л");

    private SmartSearchBridge() {
    }

    public static boolean containsSmart(
            CharSequence haystack,
            CharSequence needle
    ) {
        return containsSmart(
                haystack,
                needle,
                true
        );
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
                normalize(query);

        if (normQuery.isEmpty()) {
            return true;
        }

        String joinedTarget =
                joinTargets(targets);

        if (joinedTarget.isEmpty()) {
            return false;
        }

        /*
         * Direct compact comparison:
         *
         * "человекпаук"
         * matches
         * "Человек-паук"
         */
        String compactQuery =
                compact(normQuery);

        String compactTarget =
                compact(joinedTarget);

        if (compactQuery.length() >= 4
                && compactTarget.contains(compactQuery)) {
            return true;
        }

        /*
         * Normal query + keyboard-layout correction.
         */
        List<String> variants =
                new ArrayList<>();

        addUnique(
                variants,
                normQuery
        );

        String keyboardVariant =
                keyboardLayoutSwap(normQuery);

        if (!keyboardVariant.equals(normQuery)
                && !keyboardVariant.isEmpty()) {

            addUnique(
                    variants,
                    keyboardVariant
            );
        }

        List<String> targetTokens =
                tokenize(joinedTarget);

        if (targetTokens.isEmpty()) {
            return false;
        }

        /*
         * Every query token must be represented
         * somewhere in the target.
         */
        for (String variant : variants) {

            if (joinedTarget.contains(variant)) {
                return true;
            }

            List<String> queryTokens =
                    tokenize(variant);

            if (queryTokens.isEmpty()) {
                continue;
            }

            boolean allFound = true;

            for (String queryToken : queryTokens) {

                boolean found = false;

                for (String targetToken : targetTokens) {

                    if (tokensMatch(
                            queryToken,
                            targetToken
                    )) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    allFound = false;
                    break;
                }
            }

            if (allFound) {
                return true;
            }
        }

        return false;
    }

    private static void addUnique(
            List<String> list,
            String value
    ) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private static String joinTargets(
            String... targets
    ) {
        StringBuilder joined =
                new StringBuilder();

        if (targets == null) {
            return "";
        }

        for (String target : targets) {

            if (target == null) {
                continue;
            }

            String normalized =
                    normalize(target);

            if (normalized.isEmpty()) {
                continue;
            }

            if (joined.length() > 0) {
                joined.append(' ');
            }

            joined.append(normalized);
        }

        return joined.toString().trim();
    }

    public static String normalize(
            String text
    ) {
        if (text == null) {
            return "";
        }

        String value =
                text.toLowerCase(Locale.ROOT);

        /*
         * Normalize Unicode forms first.
         * This helps with full-width and compatibility
         * characters without affecting ordinary text.
         */
        value =
                Normalizer.normalize(
                        value,
                        Normalizer.Form.NFKC
                );

        /*
         * Russian ё/е should be interchangeable.
         */
        value =
                value.replace('ё', 'е');

        /*
         * Collapse all whitespace and control
         * characters to a single normal space.
         */
        StringBuilder result =
                new StringBuilder(
                        value.length()
                );

        boolean pendingSpace = false;

        for (int i = 0;
             i < value.length();) {

            int cp =
                    value.codePointAt(i);

            i +=
                    Character.charCount(cp);

            if (Character.isISOControl(cp)
                    || Character.isWhitespace(cp)) {

                pendingSpace = true;
                continue;
            }

            if (pendingSpace
                    && result.length() > 0) {

                result.append(' ');
            }

            pendingSpace = false;

            result.appendCodePoint(cp);
        }

        return result.toString().trim();
    }

    private static List<String> tokenize(
            String text
    ) {
        ArrayList<String> result =
                new ArrayList<>();

        if (text == null
                || text.isEmpty()) {
            return result;
        }

        java.util.regex.Matcher matcher =
                TOKEN_REGEX.matcher(text);

        while (matcher.find()) {
            result.add(
                    matcher.group()
            );
        }

        return result;
    }

    private static boolean tokensMatch(
            String queryToken,
            String targetToken
    ) {
        if (queryToken.equals(targetToken)) {
            return true;
        }

        int minLen =
                Math.min(
                        queryToken.length(),
                        targetToken.length()
                );

        int maxLen =
                Math.max(
                        queryToken.length(),
                        targetToken.length()
                );

        /*
         * Very short words remain exact-only.
         * This prevents fuzzy matching from becoming
         * too broad for tokens such as:
         *
         * "он", "мы", "ты", "я".
         */
        if (minLen <= 3) {
            return false;
        }

        /*
         * Safe prefix/stem matching.
         *
         * Examples:
         * фильм -> фильма
         * человек -> человека
         * машина -> машины
         */
        if (safePrefixMatch(
                queryToken,
                targetToken,
                minLen,
                maxLen
        )) {
            return true;
        }

        /*
         * Do not allow very different word lengths
         * to become fuzzy matches.
         */
        if (maxLen - minLen > 3) {
            return false;
        }

        int allowedDistance;

        if (maxLen <= 6) {
            allowedDistance = 1;
        } else if (maxLen <= 10) {
            allowedDistance = 2;
        } else {
            allowedDistance = 3;
        }

        /*
         * Damerau-Levenshtein also recognizes
         * adjacent character swaps.
         *
         * Example:
         * челвоек -> человек
         */
        return damerauLevenshtein(
                queryToken,
                targetToken,
                allowedDistance
        ) <= allowedDistance;
    }

    private static boolean safePrefixMatch(
            String a,
            String b,
            int minLen,
            int maxLen
    ) {
        if (minLen < 4) {
            return false;
        }

        int allowedTail;

        if (minLen <= 6) {
            allowedTail = 2;
        } else {
            allowedTail = 3;
        }

        if (maxLen - minLen > allowedTail) {
            return false;
        }

        return a.startsWith(b)
                || b.startsWith(a);
    }

    /*
     * Converts text typed in the wrong keyboard layout.
     *
     * Example:
     *
     * ghbdtn -> привет
     *
     * and in the opposite direction:
     *
     * привет -> ghbdtn
     */
    private static String keyboardLayoutSwap(
            String text
    ) {
        StringBuilder result =
                new StringBuilder(
                        text.length()
                );

        for (int i = 0;
             i < text.length();
             i++) {

            char original =
                    text.charAt(i);

            char lower =
                    Character.toLowerCase(
                            original
                    );

            char mapped =
                    mapLatinToRussian(
                            lower
                    );

            if (mapped != 0) {

                result.append(
                        Character.isUpperCase(original)
                                ? Character.toUpperCase(mapped)
                                : mapped
                );

                continue;
            }

            mapped =
                    mapRussianToLatin(
                            lower
                    );

            if (mapped != 0) {

                result.append(
                        Character.isUpperCase(original)
                                ? Character.toUpperCase(mapped)
                                : mapped
                );

                continue;
            }

            result.append(original);
        }

        return normalize(
                result.toString()
        );
    }

    private static char mapLatinToRussian(
            char c
    ) {
        int index =
                EN_LETTERS.indexOf(c);

        if (index >= 0
                && index < RU_LETTERS.length()) {

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

    private static char mapRussianToLatin(
            char c
    ) {
        int index =
                RU_LETTERS.indexOf(c);

        if (index >= 0
                && index < EN_LETTERS.length()) {

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

    /*
     * Removes spaces, punctuation and separators.
     *
     * "Spider-Man"
     * becomes
     * "spiderman"
     */
    private static String compact(
            String text
    ) {
        StringBuilder result =
                new StringBuilder(
                        text.length()
                );

        for (int i = 0;
             i < text.length();) {

            int cp =
                    text.codePointAt(i);

            i +=
                    Character.charCount(cp);

            if (Character.isLetter(cp)
                    || Character.isDigit(cp)) {

                result.appendCodePoint(cp);
            }
        }

        return result.toString();
    }

    /*
     * Damerau-Levenshtein with a small
     * maximum-distance cutoff.
     */
    private static int damerauLevenshtein(
            String a,
            String b,
            int maxDistance
    ) {
        int n = a.length();
        int m = b.length();

        if (Math.abs(n - m)
                > maxDistance) {

            return maxDistance + 1;
        }

        int[] prevPrev =
                new int[m + 1];

        int[] prev =
                new int[m + 1];

        int[] current =
                new int[m + 1];

        for (int j = 0;
             j <= m;
             j++) {

            prev[j] = j;
        }

        for (int i = 1;
             i <= n;
             i++) {

            current[0] = i;

            int rowMin =
                    current[0];

            for (int j = 1;
                 j <= m;
                 j++) {

                int cost =
                        a.charAt(i - 1)
                                == b.charAt(j - 1)
                                ? 0
                                : 1;

                int best =
                        Math.min(
                                Math.min(
                                        prev[j] + 1,
                                        current[j - 1] + 1
                                ),
                                prev[j - 1] + cost
                        );

                /*
                 * Adjacent transposition.
                 */
                if (i > 1
                        && j > 1
                        && a.charAt(i - 1)
                                == b.charAt(j - 2)
                        && a.charAt(i - 2)
                                == b.charAt(j - 1)) {

                    best =
                            Math.min(
                                    best,
                                    prevPrev[j - 2] + 1
                            );
                }

                current[j] = best;

                rowMin =
                        Math.min(
                                rowMin,
                                best
                        );
            }

            /*
             * Fast exit when the whole row
             * is already outside the allowed
             * distance.
             */
            if (rowMin > maxDistance) {
                return maxDistance + 1;
            }

            int[] temp =
                    prevPrev;

            prevPrev =
                    prev;

            prev =
                    current;

            current =
                    temp;
        }

        return prev[m];
    }
}
