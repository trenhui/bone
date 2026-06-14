package com.bone.platform.alert;

public interface AlertService {
  void sendAlert(AlertMessage message);

  void sendAlert(AlertLevel level, String message, String bu);
}
