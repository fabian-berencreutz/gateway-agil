package se.iths.fabian.gatewayagil.service;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import se.iths.fabian.gatewayagil.dto.AuthServiceResponse;
import se.iths.fabian.gatewayagil.dto.LoginResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class GatewayProxyService {
    private final String authServerUrl;
    private final String productServiceUrl;
    private final String orderServiceUrl;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GatewayProxyService(
            @Value("${app.auth-server-url}") String authServerUrl,
            @Value("${app.product-service-url}") String productServiceUrl,
            @Value("${app.order-service-url}") String orderServiceUrl,
            @Value("${app.cookie.secure:false}") boolean cookieSecure,
            @Value("${app.cookie.same-site:Lax}") String cookieSameSite,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.authServerUrl = authServerUrl;
        this.productServiceUrl = productServiceUrl;
        this.orderServiceUrl = orderServiceUrl;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public ResponseEntity<String> products(String authorization) {
        return forwardGet(productServiceUrl, "/products", authorization);
    }

    public ResponseEntity<String> productById(Long id, String authorization) {
        return forwardGet(productServiceUrl, "/products/" + id, authorization);
    }

    public ResponseEntity<String> createProduct(String requestBody, String authorization, String contentType) {
        return forwardPost(productServiceUrl, "/products", requestBody, authorization, contentType);
    }

    public ResponseEntity<String> deleteProduct(Long id, String authorization) {
        return forwardDelete(productServiceUrl, "/products/" + id, authorization);
    }

    public ResponseEntity<?> login(String requestBody, String contentType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(authServerUrl + "/auth/login"))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                AuthServiceResponse authResponse = objectMapper.readValue(response.body(), AuthServiceResponse.class);

                ResponseCookie cookie = ResponseCookie
                        .from("access_token", authResponse.accessToken())
                        .httpOnly(true)
                        .secure(cookieSecure)
                        .sameSite(cookieSameSite)
                        .path("/")
                        .maxAge(Duration.ofSeconds(authResponse.expiresIn()))
                        .build();

                LoginResponse loginResponse = new LoginResponse(
                        authResponse.expiresIn(),
                        authResponse.subject(),
                        authResponse.roles()
                );

                return ResponseEntity.status(response.statusCode())
                        .header(HttpHeaders.SET_COOKIE, cookie.toString())
                        .body(loginResponse);
            }

            ResponseEntity.BodyBuilder result = ResponseEntity.status(response.statusCode());
            response.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value -> result.header(HttpHeaders.CONTENT_TYPE, value));
            return result.body(response.body());
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Kunde inte nå bakomliggande tjänst");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Anropet avbröts");
        }
    }

    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie
                .from("access_token", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        return ResponseEntity
                .noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie.toString()
                )
                .build();
    }

    public ResponseEntity<String> createOrder(
            String requestBody,
            String authorization,
            String contentType
    ) {
        return forwardPost(
                orderServiceUrl,
                "/order",
                requestBody,
                authorization,
                contentType
        );
    }

    private ResponseEntity<String> forwardGet(
            String baseUrl,
            String path,
            String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        return send(builder, authorization);
    }

    private ResponseEntity<String> forwardPost(
            String baseUrl,
            String path,
            String requestBody,
            String authorization,
            String contentType) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody != null ? requestBody : ""));
        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        return send(builder, authorization);
    }

    private ResponseEntity<String> forwardDelete(
            String baseUrl,
            String path,
            String authorization) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .DELETE();
        return send(builder, authorization);
    }

    private ResponseEntity<String> forwardPut(
            String baseUrl,
            String path,
            String requestBody,
            String authorization,
            String contentType) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .PUT(HttpRequest.BodyPublishers.ofString(requestBody != null ? requestBody : ""));
        if (contentType != null && !contentType.isBlank()) {
            builder.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        return send(builder, authorization);
    }

    private ResponseEntity<String> send(HttpRequest.Builder builder, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        try {
            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == HttpStatus.NO_CONTENT.value()) {
                return ResponseEntity.noContent().build();
            }

            ResponseEntity.BodyBuilder result =
                    ResponseEntity.status(response.statusCode());
            response.headers().firstValue(HttpHeaders.CONTENT_TYPE)
                    .ifPresent(value -> result.header(
                            HttpHeaders.CONTENT_TYPE, value
                    ));
            return result.body(response.body());
        } catch (IOException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Kunde inte nå bakomliggande tjänst");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Anropet avbröts");
        }
    }
}