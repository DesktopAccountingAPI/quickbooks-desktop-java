package com.desktopaccountingapi.quickbooksdesktop.core;

/**
 * A value of an open enum: a generated final class with one constant per known value (for example
 * {@code LeadStatus.HOT}) and {@code of(String)} for any other value. A value added to the API after
 * this SDK version reads without failing; {@code known()} on the generated class returns its
 * {@code Known} constant, or {@code UNRECOGNIZED}, for an exhaustive {@code switch}.
 */
public interface OpenEnumValue {
    /**
     * The wire value.
     *
     * @return the value as the API sends it
     */
    String value();

    /**
     * Whether this SDK version knows the value.
     *
     * @return true for a documented value
     */
    boolean isKnown();
}
