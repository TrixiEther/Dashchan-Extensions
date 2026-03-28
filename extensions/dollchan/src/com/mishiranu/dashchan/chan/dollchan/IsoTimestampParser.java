package com.mishiranu.dashchan.chan.dollchan;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IsoTimestampParser {

    private static final Pattern OFFSET_WITH_COLON =
            Pattern.compile("([+-]\\d{2}):(\\d{2})$");

    private static final SimpleDateFormat SDF =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);

    public static long parseToMillis(String input) throws ParseException {
        if (input == null) {
            throw new ParseException("Input is null", 0);
        }

        String normalized = normalizeIsoOffset(input);
        Date date = SDF.parse(normalized);

        if (date == null) {
            throw new ParseException("Could not parse date: " + input, 0);
        }

        return date.getTime();
    }

    private static String normalizeIsoOffset(String input) {
        String s = input.trim();

        // 2026-03-23T19:32:26Z -> +0000
        if (s.endsWith("Z")) {
            return s.substring(0, s.length() - 1) + "+0000";
        }

        // 2026-03-23T19:32:26+02:00 -> +0200
        Matcher m = OFFSET_WITH_COLON.matcher(s);
        if (m.find()) {
            return m.replaceFirst(m.group(1) + m.group(2));
        }

        return s;
    }
}