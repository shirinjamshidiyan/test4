package com.shirin.notificationservice.adapters.outbound.persistence;

// Inbox status represents state of message processing in this service.
// PROCESSING: eligible for retry attempts.
// DONE: successfully processed (email sent), any duplicates must be skipped.
public enum InboxStatus {
  PROCESSING,
  DONE
}
