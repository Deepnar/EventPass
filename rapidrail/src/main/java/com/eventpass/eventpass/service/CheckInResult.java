package com.eventpass.eventpass.service;

/** Business outcome of a check-in / validation attempt. */
public enum CheckInResult {
    VALID,
    ALREADY_USED,
    CANCELLED,
    INVALID,
    WRONG_EVENT
}
