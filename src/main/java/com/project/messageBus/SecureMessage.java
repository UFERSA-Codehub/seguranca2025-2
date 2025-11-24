package com.project.messageBus;

import com.project.security.SessionKeys;

public interface SecureMessage {

    byte[] encrypt(SessionKeys keys) throws Exception;

    Message decrypt(byte[] encryptedData, SessionKeys keys) throws Exception;
}
