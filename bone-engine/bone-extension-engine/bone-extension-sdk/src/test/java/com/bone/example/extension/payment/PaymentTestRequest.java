package com.bone.example.extension.payment;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTestRequest extends PaymentRequest {
  private String couponId;
  private int pointsToDeduct;
}
