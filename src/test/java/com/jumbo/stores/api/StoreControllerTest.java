package com.jumbo.stores.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.jumbo.stores.api.generated.model.StoreResponse;
import com.jumbo.stores.domain.InvalidCoordinatesException;
import com.jumbo.stores.domain.Store;
import com.jumbo.stores.service.HaversineDistance;
import com.jumbo.stores.service.SearchProperties;
import com.jumbo.stores.service.StoreService;
import com.jumbo.stores.support.TestObjects;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class StoreControllerTest {
    @Test
    void mapsEveryFieldWithoutRoundingOrCombiningAddressComponents() {
        var store = TestObjects.store().id("distinct-id").addressName("Distinct address").city("Distinct city")
                .postalCode("1234 AB").street("Distinct street").street2("42").street3("Rear entrance")
                .coordinates(TestObjects.coordinates().latitude(51.234567).longitude(6.765432).build()).build();
        var response = StoreController.toResponse(TestObjects.nearestStore()
                .store(store).distanceKm(12.3456789012345).build());
        assertThat(response)
                .returns("distinct-id", StoreResponse::getId)
                .returns("Distinct address", StoreResponse::getAddressName)
                .returns("Distinct city", StoreResponse::getCity)
                .returns("1234 AB", StoreResponse::getPostalCode)
                .returns("Distinct street", StoreResponse::getStreet)
                .returns("42", StoreResponse::getStreet2)
                .returns("Rear entrance", StoreResponse::getStreet3)
                .returns(51.234567, StoreResponse::getLatitude)
                .returns(6.765432, StoreResponse::getLongitude)
                .returns(12.3456789012345, StoreResponse::getDistanceKm);
    }

    @Test
    void preservesNormalizedOptionalAddressFields() {
        var nearest = TestObjects.nearestStore()
                .store(TestObjects.store().street2(null).street3(null).build()).build();
        var response = StoreController.toResponse(nearest);
        assertThat(response.getStreet2()).isEmpty();
        assertThat(response.getStreet3()).isEmpty();
    }

    @Test
    void returnsJsonWithConfiguredDefaultAndServiceOrdering() {
        var controller = controller(3, stores(5));
        var response = controller.findNearestStores(52.0907, 5.1214);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStores().stream().map(store -> store.getId())).containsExactly("store-0", "store-1", "store-2");
        assertThat(body.getStores().getFirst().getDistanceKm()).isZero();
    }

    @Test
    void emptyRepositoryReturnsEmptyStores() {
        var body = controller(5, List.of()).findNearestStores(52.0, 5.0).getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStores()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"NaN, 5", "Infinity, 5", "-Infinity, 5", "91, 5", "-91, 5",
            "52, NaN", "52, Infinity", "52, -Infinity", "52, 181", "52, -181"})
    void domainCoordinateFailuresBecomeSpecificInputErrors(double latitude, double longitude) {
        assertThatThrownBy(() -> controller(5, stores(1)).findNearestStores(latitude, longitude))
                .isInstanceOf(InvalidCoordinatesException.class);
    }

    @Test
    void unrelatedIllegalArgumentsRemainServerFailures() {
        var failure = new IllegalArgumentException("internal repository failure");
        var properties = new SearchProperties(5);
        var service = new StoreService(() -> { throw failure; }, properties, new HaversineDistance());
        var controller = new StoreController(service);
        assertThatIllegalArgumentException().isThrownBy(() -> controller.findNearestStores(52.0, 5.0))
                .isSameAs(failure);
    }

    private static StoreController controller(int maximum, List<Store> stores) {
        var properties = new SearchProperties(maximum);
        return new StoreController(new StoreService(() -> stores, properties, new HaversineDistance()));
    }

    private static List<Store> stores(int count) {
        return IntStream.range(0, count).mapToObj(index -> TestObjects.store().id("store-" + index).build())
                .toList();
    }
}
