package com.desktopaccountingapi.quickbooksdesktop.core;

/** Version information of this SDK build. */
public final class SdkInfo {
    private SdkInfo() {}

    /** SDK version (SemVer). */
    public static final String VERSION = "0.3.0";

    /** {@code info.version} of the API contract the SDK was generated from. */
    public static final String API_VERSION = "1.0.0";

    /** SHA-256 of the OpenAPI contract the SDK was generated from (also in {@code .daapi-sdk.json}). */
    public static final String CONTRACT_SHA256 = "79b06eb2008381661d24d3bd5e23643483ec1bf9b70d5f4cdff2a2a71e22fe94";

    /** {@code User-Agent} sent with every request. */
    public static final String USER_AGENT = "desktopaccountingapi-java/" + VERSION;
}
