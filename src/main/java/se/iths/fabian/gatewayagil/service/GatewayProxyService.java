package se.iths.fabian.gatewayagil.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import se.iths.fabian.gatewayagil.dto.AuthServiceResponse;
import se.iths.fabian.gatewayagil.dto.LoginResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
/**
 * Service som vidarebefordrar HTTP-anrop från gatewayen
 * till webbshoppens bakomliggande mikrotjänster.
 *
 * Hanterar kommunikation med Auth Service, Product Service
 * och Order Service samt vidarebefordrar JWT-token.
 *
 * Hanterar även inloggning och utloggning med HTTP-cookies.
 */
@Service
public class GatewayProxyService {
    private final String authServerUrl;
    private final String productServiceUrl;
    private final String orderServiceUrl;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    /**
     * Skapar en GatewayProxyService med konfiguration för mikrotjänsterna.
     *
     * @param authServerUrl adress till Auth Service
     * @param productServiceUrl adress till Product Service
     * @param orderServiceUrl adress till Order Service
     * @param cookieSecure anger om cookies endast ska skickas via HTTPS
     * @param cookieSameSite inställning för cookies SameSite-attribut
     * @param httpClient HTTP-klient för anrop till mikrotjänster
     * @param objectMapper används för att läsa och konvertera JSON
     */
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
    /**
     * Hämtar alla produkter från Product Service.
     *
     * @param authorization JWT-token i Authorization-headern
     * @return HTTP-svar med produktlistan från Product Service
     */
    public ResponseEntity<String> products(String authorization) {
        return forwardGet(productServiceUrl, "/products", authorization);
    }
    /**
     * Hämtar en specifik produkt från Product Service via produktens ID.
     *
     * @param id ID för produkten som ska hämtas
     * @param authorization JWT-token i Authorization-headern
     * @return HTTP-svar med produktinformationen från Product Service
     */
    public ResponseEntity<String> productById(Long id, String authorization) {
        return forwardGet(productServiceUrl, "/products/" + id, authorization);
    }
    /**
     * Vidarebefordrar en begäran om att skapa en produkt till Product Service.
     *
     * @param requestBody produktinformationen i JSON-format
     * @param authorization JWT-token i Authorization-headern
     * @param contentType innehållstypen för HTTP-anropet
     * @return HTTP-svar från Product Service
     */
    public ResponseEntity<String> createProduct(String requestBody, String authorization, String contentType) {
        return forwardPost(productServiceUrl, "/products", requestBody, authorization, contentType);
    }
    /**
     * Ökar lagersaldot för en eller flera produkter via Product Service.
     *
     * @param requestBody produkt-ID och antal som ska läggas till i JSON-format
     * @param authorization JWT-token i Authorization-headern
     * @param contentType innehållstypen för HTTP-anropet
     * @return HTTP-svar från Product Service med uppdaterat lagersaldo
     */
    public ResponseEntity<String> increaseProductStock(
            String requestBody,
            String authorization,
            String contentType) {

        return forwardPost(
                productServiceUrl,
                "/products/stock/increase",
                requestBody,
                authorization,
                contentType
        );
    }
    /**
     * Tar bort en produkt via Product Service.
     *
     * @param id ID för produkten som ska tas bort
     * @param authorization JWT-token i Authorization-headern
     * @return HTTP-svar från Product Service
     */
    public ResponseEntity<String> deleteProduct(Long id, String authorization) {
        return forwardDelete(productServiceUrl, "/products/" + id, authorization);
    }
    /**
     * Skickar inloggningsuppgifter till Auth Service.
     *
     * Vid lyckad inloggning skapas en HTTP-cookie
     * som innehåller användarens JWT-token.
     *
     * @param requestBody inloggningsuppgifter i JSON-format
     * @param contentType innehållstypen för HTTP-anropet
     * @return HTTP-svar med användarinformation och autentiseringscookie
     */
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
    /**
     * Loggar ut användaren genom att ta bort autentiseringscookien.
     *
     * @return HTTP-svar med status 204 No Content
     *         och en cookie som upphör att gälla
     */
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
    /**
     * Vidarebefordrar en begäran om att skapa en order till Order Service.
     *
     * @param requestBody orderinformationen i JSON-format
     * @param authorization JWT-token i Authorization-headern
     * @param contentType innehållstypen för HTTP-anropet
     * @return HTTP-svar från Order Service
     */
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
    /**
     * Vidarebefordrar ett HTTP GET-anrop till en mikrotjänst.
     *
     * @param baseUrl mikrotjänstens basadress
     * @param path sökvägen till API-endpointen
     * @param authorization JWT-token i Authorization-headern
     * @return HTTP-svar från mikrotjänsten
     */
    private ResponseEntity<String> forwardGet(
            String baseUrl,
            String path,
            String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET();
        return send(builder, authorization);
    }
    /**
     * Vidarebefordrar ett HTTP POST-anrop till en mikrotjänst.
     *
     * @param baseUrl mikrotjänstens basadress
     * @param path sökvägen till API-endpointen
     * @param requestBody innehållet som skickas i HTTP-anropet
     * @param authorization JWT-token i Authorization-headern
     * @param contentType innehållstypen för HTTP-anropet
     * @return HTTP-svar från mikrotjänsten
     */
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
    /**
     * Vidarebefordrar ett HTTP DELETE-anrop till en mikrotjänst.
     *
     * @param baseUrl mikrotjänstens basadress
     * @param path sökvägen till API-endpointen
     * @param authorization JWT-token i Authorization-headern
     * @return HTTP-svar från mikrotjänsten
     */
    private ResponseEntity<String> forwardDelete(
            String baseUrl,
            String path,
            String authorization) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .DELETE();
        return send(builder, authorization);
    }
    /**
     * Vidarebefordrar ett HTTP PUT-anrop till en mikrotjänst.
     *
     * @param baseUrl mikrotjänstens basadress
     * @param path sökvägen till API-endpointen
     * @param requestBody innehållet som skickas i HTTP-anropet
     * @param authorization JWT-token i Authorization-headern
     * @param contentType innehållstypen för HTTP-anropet
     * @return HTTP-svar från mikrotjänsten
     */
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
    /**
     * Skickar ett HTTP-anrop till en bakomliggande mikrotjänst.
     *
     * Lägger till Authorization-headern om en JWT-token finns.
     * Returnerar mikrotjänstens HTTP-status och svar.
     * Hanterar även kommunikationsfel.
     *
     * @param builder HTTP-anropet som ska skickas
     * @param authorization JWT-token i Authorization-headern
     * @return HTTP-svar från mikrotjänsten eller ett felmeddelande
     */
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
    /**
     * Hämtar information om den inloggade användaren från Auth Service.
     *
     * Skickar användarens JWT-token till endpointen /auth/me.
     *
     * @param accessToken användarens JWT-token
     * @return HTTP-svar med användarinformation från Auth Service
     */
    public ResponseEntity<String> session(String accessToken) {
        return forwardGet(
                authServerUrl,
                "/auth/me",
                "Bearer " + accessToken
        );
    }
}