package com.desktopaccountingapi.quickbooksdesktop.core;

/** Version information of this SDK build. */
public final class SdkInfo {
    private SdkInfo() {}

    /** SDK version (SemVer). */
    public static final String VERSION = "0.9.1";

    /** {@code info.version} of the API contract the SDK was generated from. */
    public static final String API_VERSION = "1.0.0";

    /** SHA-256 of the OpenAPI contract the SDK was generated from (also in {@code .daapi-sdk.json}). */
    public static final String CONTRACT_SHA256 = "7ebdb557fe6b2a1d3b09b8be62a7ff46baa52140ba5e5fbc967e3fc707d0c7ac";

    /** {@code User-Agent} sent with every request. */
    public static final String USER_AGENT = "desktopaccountingapi-java/" + VERSION;
}
