package com.aniketh.retail.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
class OrderClient {
    private final RestClient client;

    OrderClient(RestClient.Builder builder, @Value("${payment.order-service-url}") String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    OrderSnapshot get(UUID id) {
        try {
            var order = client.get().uri("/api/orders/{id}", id).retrieve().body(OrderSnapshot.class);
            if (order == null || !id.equals(order.id()) || order.status() == null
                    || order.totalAmount() == null || order.currency() == null) {
                throw unavailable();
            }
            return order;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found.");
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order service is unavailable.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderSnapshot(UUID id, String status, BigDecimal totalAmount, String currency) {
    }
}
