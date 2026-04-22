
package com.bone.blueprint.domain.model.order.vo;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ShippingAddress {

    private final String recipientName;
    private final String phone;
    private final String province;
    private final String city;
    private final String district;
    private final String detail;
    private final String postalCode;

    public static ShippingAddress of(String recipientName, String phone, String province,
                                      String city, String district, String detail, String postalCode) {
        if (recipientName == null || recipientName.isBlank()) {
            throw new IllegalArgumentException("Recipient name cannot be null or blank");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Phone cannot be null or blank");
        }
        if (province == null || city == null || district == null || detail == null) {
            throw new IllegalArgumentException("Address fields cannot be null");
        }
        return new ShippingAddress(recipientName, phone, province, city, district, detail, postalCode);
    }
}

