package com.eventpass.eventpass.service;

/** Carries a check-in business outcome (ALREADY_USED, CANCELLED, ...) up to the controller. */
public class CheckInException extends RuntimeException {
    private final CheckInResult result;

    public CheckInException(CheckInResult result, String message) {
        super(message);
        this.result = result;
    }

    public CheckInResult getResult() { return result; }
}
