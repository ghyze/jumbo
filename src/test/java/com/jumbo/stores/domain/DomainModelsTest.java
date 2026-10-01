package com.jumbo.stores.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.jumbo.stores.support.TestObjects;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DomainModelsTest {
    @Test
    void normalizesOptionalAddressParts() {
        var store = TestObjects.store().street2(null).street3(null).build();
        assertThat(store.street2()).isEmpty();
        assertThat(store.street3()).isEmpty();
    }

    @Test
    void rejectsInvalidStoreFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().id(" ").build());
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().addressName(null).build());
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().city("").build());
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().postalCode("\t").build());
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().street(null).build());
        assertThatNullPointerException().isThrownBy(() -> TestObjects.store().coordinates(null).build());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\r\n", "\u2003"})
    void domainProtectsEveryRequiredStoreFieldWithoutRepository(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().id(value).build());
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().addressName(value).build());
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().city(value).build());
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().postalCode(value).build());
        assertThatIllegalArgumentException().isThrownBy(() -> TestObjects.store().street(value).build());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidDistance(double distance) {
        assertThatIllegalArgumentException().isThrownBy(
                () -> TestObjects.nearestStore().distanceKm(distance).build());
    }

    @Test
    void searchResultDefensivelyCopiesBothLists() {
        var stores = new ArrayList<>(List.of(TestObjects.nearestStore().build()));
        var warnings = new ArrayList<>(List.of(TestObjects.searchWarning().build()));
        var result = new SearchResult(stores, warnings);
        stores.clear();
        warnings.clear();
        assertThat(result.stores()).hasSize(1);
        assertThat(result.warnings()).hasSize(1);
        assertThatThrownBy(() -> result.stores().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.warnings().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validatesWarningsAndNearestStore() {
        assertThatNullPointerException().isThrownBy(() -> new SearchWarning(null, "message"));
        assertThatIllegalArgumentException().isThrownBy(() -> new SearchWarning(WarningCode.OUTSIDE_SUPPORTED_AREA, null));
        assertThatNullPointerException().isThrownBy(() -> new NearestStore(null, 0));
        assertThat(TestObjects.nearestStore().distanceKm(0).build().distanceKm()).isZero();
    }
}
