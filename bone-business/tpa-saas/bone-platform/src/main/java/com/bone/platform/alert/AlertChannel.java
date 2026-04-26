package com.bone.platform.alert;

public interface AlertChannel {
    AlertChannelType channelType();
    void send(AlertMessage message) throws AlertException;
}
