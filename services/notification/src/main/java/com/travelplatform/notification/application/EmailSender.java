package com.travelplatform.notification.application;

/**
 * Port for the optional email channel. v1 ships a logging implementation; a real
 * SMTP/provider implementation must not change the call sites (宪法 V-style seam).
 */
public interface EmailSender {

    void send(String userId, String title, String body);
}
