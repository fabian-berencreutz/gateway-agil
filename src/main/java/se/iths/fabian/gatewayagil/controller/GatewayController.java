package se.iths.fabian.gatewayagil.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.iths.fabian.gatewayagil.service.GatewayProxyService;

@RestController
@RequestMapping({"/gateway", ""})
public class GatewayController {
    private final GatewayProxyService gatewayProxyService;

    public GatewayController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }

    @GetMapping("/products")
    public ResponseEntity<String> products(
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {
        return gatewayProxyService.products(resolveAuthHeader(accessToken, request));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<String> productById(
            @PathVariable Long id,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {
        return gatewayProxyService.productById(id, resolveAuthHeader(accessToken, request));
    }

    @PostMapping("/products")
    public ResponseEntity<String> createProduct(
            @RequestBody String requestBody,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {
        return gatewayProxyService.createProduct(
                requestBody,
                resolveAuthHeader(accessToken, request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }
    @PostMapping("/products/stock/increase")
    public ResponseEntity<String> increaseProductStock(
            @RequestBody String requestBody,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {

        return gatewayProxyService.increaseProductStock(
                requestBody,
                resolveAuthHeader(accessToken, request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<String> deleteProduct(
            @PathVariable Long id,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {
        return gatewayProxyService.deleteProduct(id, resolveAuthHeader(accessToken, request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(
            @RequestBody String requestBody,
            HttpServletRequest request) {
        return gatewayProxyService.login(
                requestBody,
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return gatewayProxyService.logout();
    }

    @PostMapping({"/order", "/orders"})
    public ResponseEntity<String> createOrder(
            @RequestBody String requestBody,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {
        return gatewayProxyService.createOrder(
                requestBody,
                resolveAuthHeader(accessToken, request),
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }

    private String resolveAuthHeader(String accessToken, HttpServletRequest request) {
        if (accessToken != null && !accessToken.isBlank()) {
            return "Bearer " + accessToken;
        }
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }
}
