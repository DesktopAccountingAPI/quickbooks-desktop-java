package com.desktopaccountingapi.quickbooksdesktop.core;

/** Version information of this SDK build. */
public final class SdkInfo {
    private SdkInfo() {}

    /** SDK version (SemVer). */
    public static final String VERSION = "0.11.0";

    /** {@code info.version} of the API contract the SDK was generated from. */
    public static final String API_VERSION = "1.0.0";

    /** SHA-256 of the OpenAPI contract the SDK was generated from (also in {@code .daapi-sdk.json}). */
    public static final String CONTRACT_SHA256 = "b091ed70ce41b5dc0932b12c56657597225671a1778521347f2cb41f92edc8fb";

    /** {@code User-Agent} sent with every request. */
    public static final String USER_AGENT = "desktopaccountingapi-java/" + VERSION;
}
