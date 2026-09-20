package com.admire.cars.runner.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OutcomeType {
    MEDIA_BY("Media By", "MEDIABY"),
    STATIC_IP("Static IP", "STATIC IP"),
    DYNAMIC_IP("Dynamic IP", "DYNAMIC IP"),
    CLOUD_PHONE("Cloud Phone", "CLOUD PHONE"),
    VPN("VPN", "VPN"),
    VPS("VPS", "VPS"),
    ADS_POWER_BROWSER("Ads Power Broswer", "ADSPOWER BROWSER"),
    SEMRUSH("Semrush", "SEMRUSH"),
    IP_PROXY("IP Proxy", "IP PROXY"),
    OTHERS("Others", "OTHERS");

    private final String display;
    private final String normalized;

    OutcomeType(String display, String normalized) {
        this.display = display;
        this.normalized = normalized;
    }

    @JsonValue
    public String getDisplayName() {
        return display;
    }

    public String getNormalized() {
        return normalized;
    }

    @Override
    public String toString() {
        return name();
    }

    @JsonCreator
    public static OutcomeType fromString(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        // try matching name style
        try {
            return OutcomeType.valueOf(v.toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
        }
        // try matching display (case-insensitive)
        for (OutcomeType t : values()) {
            if (t.display.equalsIgnoreCase(v)) return t;
        }
        // try matching normalized (case-insensitive)
        for (OutcomeType t : values()) {
            if (t.normalized.equalsIgnoreCase(v)) return t;
        }
        // tolerant normalize
        String normalizedInput = v.toUpperCase().replaceAll("[^A-Z0-9]", "");
        for (OutcomeType t : values()) {
            String n = t.name().replaceAll("[^A-Z0-9]", "");
            if (n.equals(normalizedInput)) return t;
        }
        return null;
    }
}
