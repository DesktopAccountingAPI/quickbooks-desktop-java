package com.desktopaccountingapi.quickbooksdesktop.core;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Local secret-key format check. A secret key is {@code sk_live_} or {@code sk_test_} followed by 40
 * base62 characters; the last 6 are the base62 (zero-padded) CRC32 of the preceding 34. The client
 * checks the key before its first request so a typo fails fast with a clear message instead of a
 * 401 from the API.
 */
public final class ApiKeys {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int RANDOM = 34;
    private static final int CHECKSUM = 6;

    private ApiKeys() {}

    /**
     * Whether a string is a well-formed secret key (prefix, length, alphabet and checksum).
     *
     * @param key the candidate key
     * @return true if the key passes the local check
     */
    public static boolean isValid(String key) {
        return problem(key) == null;
    }

    /**
     * Explains why a key fails the local check, without echoing the key.
     *
     * @param key the candidate key
     * @return the problem, or null if the key is well formed
     */
    public static String problem(String key) {
        if (key == null || key.isEmpty()) return "the secret key is empty";
        String rest;
        if (key.startsWith("sk_live_")) rest = key.substring(8);
        else if (key.startsWith("sk_test_")) rest = key.substring(8);
        else return "a secret key starts with sk_live_ or sk_test_";
        if (rest.length() != RANDOM + CHECKSUM) return "a secret key has 40 characters after the sk_live_/sk_test_ prefix";
        for (int i = 0; i < rest.length(); i++) {
            if (ALPHABET.indexOf(rest.charAt(i)) < 0) return "a secret key contains only letters and digits after the prefix";
        }
        String random = rest.substring(0, RANDOM);
        if (!checksum(random).equals(rest.substring(RANDOM))) return "the secret key's checksum does not match (the key is mistyped or truncated)";
        return null;
    }

    static String checksum(String random) {
        CRC32 crc = new CRC32();
        crc.update(random.getBytes(StandardCharsets.UTF_8));
        long v = crc.getValue();
        char[] out = new char[CHECKSUM];
        for (int i = CHECKSUM - 1; i >= 0; i--) {
            out[i] = ALPHABET.charAt((int) (v % 62));
            v /= 62;
        }
        return new String(out);
    }
}
