package se.iths.fabian.gatewayagil.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se.iths.fabian.gatewayagil.service.GatewayProxyService;
/**
 * Controller för webbshoppens API Gateway.

 * Tar emot HTTP-anrop från frontend och vidarebefordrar dem
 * till rätt mikrotjänst via GatewayProxyService.

 * Hanterar anrop för produkter, lager, autentisering och ordrar.
 */

@RestController
@RequestMapping({"/gateway", ""})
public class GatewayController {
    private final GatewayProxyService gatewayProxyService;

    public GatewayController(GatewayProxyService gatewayProxyService) {
        this.gatewayProxyService = gatewayProxyService;
    }
    /**
     * Hämtar alla produkter via Product Service.
     *
     * @param accessToken JWT-token från användarens cookie
     * @param request HTTP-förfrågan med eventuell Authorization-header
     * @return HTTP-svar med produktlistan
     */
    @GetMapping("/products")
    public ResponseEntity<String> products(
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {
        return gatewayProxyService.products(resolveAuthHeader(accessToken, request));
    }
    /**
     * Hämtar en specifik produkt via dess ID.
     *
     * @param id Produktens ID
     * @param accessToken JWT-token från användarens cookie
     * @param request HTTP-förfrågan med eventuell Authorization-header
     * @return HTTP-svar med produktinformationen
     */
    @GetMapping("/products/{id}")
    public ResponseEntity<String> productById(
            @PathVariable Long id,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {
        return gatewayProxyService.productById(id, resolveAuthHeader(accessToken, request));
    }
    /**
     * Skapar en ny produkt via Product Service.
     *
     * @param requestBody Produktinformationen i JSON-format
     * @param accessToken JWT-token från användarens cookie
     * @param request HTTP-förfrågan med eventuell Authorization-header
     * @return HTTP-svar från Product Service
     */
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
    /**
     * Ökar lagersaldot för en eller flera produkter via Product Service.
     *
     * @param requestBody Produkt-ID och antal som ska läggas till i JSON-format
     * @param accessToken JWT-token från användarens cookie
     * @param request HTTP-förfrågan med eventuell Authorization-header
     * @return HTTP-svar från Product Service med uppdaterat lagersaldo
     */
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
    /**
     * Tar bort en produkt via Product Service.
     *
     * @param id ID för produkten som ska tas bort
     * @param accessToken JWT-token från användarens cookie
     * @param request HTTP-förfrågan med eventuell Authorization-header
     * @return HTTP-svar från Product Service
     */
    @DeleteMapping("/products/{id}")
    public ResponseEntity<String> deleteProduct(
            @PathVariable Long id,
            @CookieValue(name = "access_token", required = false) String accessToken,
            HttpServletRequest request) {
        return gatewayProxyService.deleteProduct(id, resolveAuthHeader(accessToken, request));
    }
    /**
     * Vidarebefordrar inloggningsuppgifter till Auth Service.
     *
     * @param requestBody Inloggningsuppgifter i JSON-format
     * @param request HTTP-förfrågan med Content-Type-header
     * @return HTTP-svar från Auth Service
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(
            @RequestBody String requestBody,
            HttpServletRequest request) {
        return gatewayProxyService.login(
                requestBody,
                request.getHeader(HttpHeaders.CONTENT_TYPE)
        );
    }
    /**
     * Loggar ut användaren genom att rensa autentiseringscookien.
     *
     * @return HTTP-svar som bekräftar utloggningen
     */
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return gatewayProxyService.logout();
    }
    /**
     * Vidarebefordrar en order till Order Service.
     *
     * @param requestBody Orderinformationen i JSON-format
     * @param accessToken JWT-token från användarens cookie
     * @param request HTTP-förfrågan med eventuell Authorization-header
     * @return HTTP-svar från Order Service
     */
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
    /**
     * Hämtar JWT-token från användarens cookie eller Authorization-header.
     *
     * @param accessToken JWT-token från användarens cookie
     * @param request HTTP-förfrågan med eventuell Authorization-header
     * @return Authorization-header med Bearer-token, eller null om ingen token finns
     */
    private String resolveAuthHeader(String accessToken, HttpServletRequest request) {
        if (accessToken != null && !accessToken.isBlank()) {
            return "Bearer " + accessToken;
        }
        return request.getHeader(HttpHeaders.AUTHORIZATION);
    }

    @GetMapping("/auth/me")
    public ResponseEntity<String> session(@CookieValue(name = "access_token", required = false) String accessToken) {
        return gatewayProxyService.session(accessToken);
    }
}
