package com.example.utils;

public final class M3uNormalizer {

    private M3uNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String normalized = input
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String[] lines = normalized.split("\\n", -1);

        StringBuilder out = new StringBuilder(normalized.length());

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.regionMatches(
                    true,
                    0,
                    "#EXTGRP:",
                    0,
                    8
            )) {
                continue;
            }

            out.append(line).append('\n');
        }

        return out.toString();
    }
}
