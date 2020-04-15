package com.engineering.challenge.solution.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ShelfType {

    WAITING("waiting"), HOT("hot"), COLD("cold"), FROZEN("frozen"), OVERFLOW("overflow");

    private String shelfType;

    ShelfType(String shelfType) {
        this.shelfType = shelfType;
    }

    @JsonValue
    public String toString() {
        return this.shelfType;
    }

    @JsonCreator
    public static ShelfType fromString(String value) {
        return ShelfType.valueOf(
            value.toUpperCase()
        );
    }
}
