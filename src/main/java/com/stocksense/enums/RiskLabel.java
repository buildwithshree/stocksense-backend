package com.stocksense.enums;

public enum RiskLabel {
    Low,
    Moderate,
    High,
    Very_High;

    // Display-safe label matching DB enum value
    public String display() {
        return this == Very_High ? "Very High" : this.name();
    }
}
