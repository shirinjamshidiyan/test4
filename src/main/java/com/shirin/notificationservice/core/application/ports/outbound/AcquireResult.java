package com.shirin.notificationservice.core.application.ports.outbound;

public enum AcquireResult {
    ACQUIRED,         // proceed with processing (first time or allowed retry)
    ALREADY_DONE,     // duplicate delivery; skip
    RETRY_EXHAUSTED   // too many failures; escalate to DLT
}