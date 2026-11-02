package com.nidasehba.chaos;

public enum ChargeStatus {
    PENDING, SUCCEEDED, FAILED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
