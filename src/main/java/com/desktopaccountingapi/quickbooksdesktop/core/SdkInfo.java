package com.desktopaccountingapi.quickbooksdesktop.core;

/** Version information of this SDK build. */
public final class SdkInfo {
    private SdkInfo() {}

    /** SDK version (SemVer). */
    public static final String VERSION = "0.8.0";

    /** {@code info.version} of the API contract the SDK was generated from. */
    public static final String API_VERSION = "1.0.0";

    /** SHA-256 of the OpenAPI contract the SDK was generated from (also in {@code .daapi-sdk.json}). */
    public static final String CONTRACT_SHA256 = "149fc7b0599dee2c0c0bde9a8f502f9d32e6215a26c5222594330c429e9b735f";

    /** {@code User-Agent} sent with every request. */
    public static final String USER_AGENT = "desktopaccountingapi-java/" + VERSION;
}
