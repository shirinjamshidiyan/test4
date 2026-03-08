package com.shirin.notificationservice.adapters.inbound.kafka;

// می‌خوای وقتی رفت DLT، به جای “همون value خام” یک Envelope (پاکت) بفرستی که هم payload اصلی داخلش
// باشد هم متادیتا و هم علت خطا. بهترین کار همین است.

public record DltEnvelope(
    String originalTopic,
    int originalPartition,
    long originalOffset,
    String originalKey,
    String failureStage,
    // DESERIALIZE | VALIDATION | PROCESSING | PUBLISH_DLT
    String errorType,
    String errorMessage,
    //   String errorStacktrace,       // optional, truncated

    String rawText, // وقتی deserialize fail when deserialize fails (best-effort)
    String rawBase64, // raw bytes always human-storable
    String eventJson // when we have a typed object (validation/processing)
    ) {}
