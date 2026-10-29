# Gateway – Agil Webbshop

API Gateway som fungerar som mellanhand mellan frontend och webbshoppens mikrotjänster.

## Funktioner

- Vidarebefordrar API-anrop från frontend till rätt mikrotjänst
- Hanterar anrop till Auth Service, Product Service och Order Service
- Vidarebefordrar JWT-token för autentisering och auktorisering
- Hanterar inloggning och utloggning med cookies
- Stöd för att öka produktens lagersaldo via gatewayen
- CORS-konfiguration för kommunikation med frontend

## Teknologier

- Java
- Spring Boot
- Spring Web
- Maven & Docker

## Starta projektet

Starta först de backend-tjänster som gatewayen ska kommunicera med.

Kör gatewayen lokalt:

```bash
./mvnw spring-boot:run
```

Gatewayen körs som standard på port `9003`.

Tjänsternas standardadresser är:

- Auth Service: `http://localhost:8080`
- Product Service: `http://localhost:8081`
- Order Service: `http://localhost:8082`

Adresserna kan ändras via miljövariablerna `AUTH_SERVER_URL`, `PRODUCT_SERVICE_URL` och `ORDER_SERVICE_URL`.

## API Endpoints

- `GET /products` – Hämta alla produkter
- `GET /products/{id}` – Hämta produkt via ID
- `POST /products/stock/increase` – Öka lagersaldo (ADMIN)
- `POST /auth/login` – Logga in
- `POST /auth/logout` – Logga ut
- `/order` – Vidarebefordra orderanrop till Order Service

## Öka lager

När en administratör klickar på **Öka lager** i frontend skickas ett POST-anrop till gatewayen på `/products/stock/increase`.

Gatewayen vidarebefordrar anropet och autentiseringsinformationen till Product Service, som uppdaterar lagersaldot.

Funktionen har testats lokalt med ett lagersaldo som ökade från 5 till 8.