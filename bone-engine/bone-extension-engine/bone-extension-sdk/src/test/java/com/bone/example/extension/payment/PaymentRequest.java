// PaymentRequest.java
package com.bone.example.extension.payment;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    protected String orderId;
    protected String userId;
    protected BigDecimal amount;
    protected String paymentMethod;
}