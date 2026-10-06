package com.desktopaccountingapi.quickbooksdesktop.core;

/** Version information of this SDK build. */
public final class SdkInfo {
    private SdkInfo() {}

    /** SDK version (SemVer). */
    public static final String VERSION = "0.1.1";

    /** {@code info.version} of the API contract the SDK was generated from. */
    public static final String API_VERSION = "1.0.0";

    /** SHA-256 of the OpenAPI contract the SDK was generated from (also in {@code .daapi-sdk.json}). */
    public static final String CONTRACT_SHA256 = "1cc3058cecb557ce1cc724d5236d36860df6bec39636e2d427c8a408bf5f2ca2";

    /** {@code User-Agent} sent with every request. */
    public static final String USER_AGENT = "desktopaccountingapi-java/" + VERSION;
}
