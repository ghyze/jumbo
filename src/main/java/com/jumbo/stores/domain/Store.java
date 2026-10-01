package com.jumbo.stores.domain;

import static com.jumbo.stores.util.StringUtil.isBlank;
import java.util.Objects;
import lombok.Builder;

@Builder
public record Store(String id, String addressName, String city, String postalCode,
                    String street, String street2, String street3, Coordinates coordinates) {
    public Store {
        requireText(id, "id");
        requireText(addressName, "addressName");
        requireText(city, "city");
        requireText(postalCode, "postalCode");
        requireText(street, "street");
        street2 = street2 == null ? "" : street2;
        street3 = street3 == null ? "" : street3;
        Objects.requireNonNull(coordinates, "coordinates");
    }

    private static void requireText(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
