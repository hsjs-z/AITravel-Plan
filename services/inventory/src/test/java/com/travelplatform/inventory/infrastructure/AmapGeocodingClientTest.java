package com.travelplatform.inventory.infrastructure;

import com.travelplatform.inventory.application.seed.SeedDtos;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapGeocodingClientTest {

    @Test
    void mapsAmapLocationToLatitudeAndLongitude() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapGeocodingClient client = new AmapGeocodingClient(builder, "test-key", 0);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://restapi.amap.com/v3/geocode/geo")))
                .andRespond(withSuccess("""
                        {"status":"1","count":"1","geocodes":[{"location":"100.180000,27.100000"}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<SeedDtos.SeedCoordinate> coordinate = client.geocode("玉龙雪山", "丽江");

        assertTrue(coordinate.isPresent());
        assertEquals(0, new BigDecimal("27.1").compareTo(coordinate.get().latitude()));
        assertEquals(0, new BigDecimal("100.18").compareTo(coordinate.get().longitude()));
        server.verify();
    }

    @Test
    void zeroResultsBecomeEmptyCoordinate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapGeocodingClient client = new AmapGeocodingClient(builder, "test-key", 0);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://restapi.amap.com/v3/geocode/geo")))
                .andRespond(withSuccess("""
                        {"status":"1","count":"0","geocodes":[]}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(client.geocode("不存在景区", "丽江").isEmpty());
        server.verify();
    }

    @Test
    void providerErrorDoesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapGeocodingClient client = new AmapGeocodingClient(builder, "test-key", 0);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://restapi.amap.com/v3/geocode/geo")))
                .andRespond(withServerError());

        assertTrue(client.geocode("玉龙雪山", "丽江").isEmpty());
        server.verify();
    }

    @Test
    void unconfiguredClientDoesNotCallAmap() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapGeocodingClient client = new AmapGeocodingClient(builder, " ", 0);

        assertTrue(client.geocode("玉龙雪山", "丽江").isEmpty());
        server.verify();
    }
}
