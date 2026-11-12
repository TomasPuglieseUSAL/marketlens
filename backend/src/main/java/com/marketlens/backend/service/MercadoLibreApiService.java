package com.marketlens.backend.service;

import com.marketlens.backend.dto.MercadoLibreUserResponse;
import com.marketlens.backend.dto.MercadoLibreUserProductResponse;
import com.marketlens.backend.dto.MercadoLibreSellerLookupResponse;
import com.marketlens.backend.dto.MercadoLibreItemResponse;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.databind.JsonNode;

@Service
public class MercadoLibreApiService {

        private static final String API_BASE_URL = "https://api.mercadolibre.com";

        private final MercadoLibreOAuthService oauthService;
        private final RestClient restClient;
        private static final Logger logger = LoggerFactory.getLogger(MercadoLibreApiService.class);

        private final ObjectMapper objectMapper = new ObjectMapper();

        public MercadoLibreApiService(
                        MercadoLibreOAuthService oauthService) {

                this.oauthService = oauthService;
                this.restClient = RestClient.create(API_BASE_URL);
        }

        public MercadoLibreItemResponse getItem(
                        String username,
                        String itemId) {

                // 1. Validar el identificador.
                if (itemId == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Identificador de publicación faltante.");
                }

                String normalizedId = itemId.trim()
                                .toUpperCase(Locale.ROOT);

                if (!normalizedId.matches("MLA\\d{6,}")) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Identificador de publicación inválido.");
                }

                // 2. Obtener el token OAuth del usuario de MarketLens.
                String accessToken = oauthService.getAccessToken(username);

                // 3. Consultar la publicación en Mercado Libre.
                JsonNode response = executeGet(
                                "/items/" + normalizedId,
                                accessToken);

                // 4. Recuperar los datos relevantes.
                String returnedId = nullableString(response, "id");

                if (!normalizedId.equals(returnedId)) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "Mercado Libre devolvió un identificador inesperado.");
                }

                Long sellerId = nullableLong(response, "seller_id");

                if (sellerId != null && sellerId <= 0) {
                        sellerId = null;
                }

                String userProductId = nullableString(response, "user_product_id");

                return new MercadoLibreItemResponse(
                                returnedId,
                                nullableString(response, "title"),
                                sellerId,
                                userProductId,
                                nullableString(response, "permalink"));
        }

        public MercadoLibreUserResponse getItemSeller(
                        String username,
                        String itemId) {

                // Primero identificar el vendedor desde el ítem.
                MercadoLibreItemResponse item = getItem(username, itemId);

                if (item.sellerId() == null) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "La publicación no contiene un vendedor identificable.");
                }

                // Después consultar sus datos públicos.
                return getPublicUser(
                                username,
                                item.sellerId());
        }

        // ---------------------------------------
        // Consultar la cuenta que autorizó OAuth
        // ---------------------------------------

        public MercadoLibreUserResponse getCurrentUser(
                        String username) {

                String accessToken = oauthService.getAccessToken(username);

                JsonNode response = executeGet(
                                "/users/me",
                                accessToken);

                JsonNode reputation = response.path("seller_reputation");

                return new MercadoLibreUserResponse(
                                nullableLong(response, "id"),
                                nullableString(response, "nickname"),
                                nullableString(response, "registration_date"),
                                nullableString(reputation, "level_id"),
                                nullableString(reputation, "power_seller_status"),
                                historicCompletedSales(reputation),
                                nullableString(reputation.path("transactions"), "period"));
        }

        // ---------------------------------------
        // Consultar un User Product de Mercado Libre
        // ---------------------------------------

        public MercadoLibreUserProductResponse getUserProduct(
                        String username,
                        String userProductId) {

                if (userProductId == null ||
                                !userProductId.matches("MLAU\\d{6,}")) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Identificador de User Product inválido.");
                }

                String accessToken = oauthService.getAccessToken(username);

                JsonNode response = executeGet(
                                "/user-products/" + userProductId,
                                accessToken);

                return new MercadoLibreUserProductResponse(
                                nullableString(response, "id"),
                                nullableString(response, "name"),
                                nullableLong(response, "user_id"),
                                nullableString(response, "domain_id"),
                                nullableString(response, "family_id"));
        }

        // ---------------------------------------
        // Comunicación HTTP
        // ---------------------------------------

        private JsonNode executeGet(
                        String path,
                        String accessToken) {

                try {
                        JsonNode response = restClient.get()
                                        .uri(path)
                                        .headers(headers -> headers.setBearerAuth(accessToken))
                                        .retrieve()
                                        .body(JsonNode.class);

                        if (response == null || response.isNull()) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_GATEWAY,
                                                "Mercado Libre devolvió una respuesta vacía.");
                        }

                        return response;

                } catch (RestClientResponseException exception) {

                        int status = exception.getStatusCode().value();
                        logger.warn(
                                        "Mercado Libre API: solicitud rechazada con HTTP {}",
                                        status);

                        if (status == 401) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_GATEWAY,
                                                "Mercado Libre rechazó el access token.");
                        }

                        if (status == 403) {

                                String error = "unknown";
                                String code = "unknown";
                                String blockedBy = "unknown";

                                try {
                                        JsonNode body = objectMapper.readTree(
                                                        exception.getResponseBodyAsString());

                                        error = safeErrorValue(
                                                        body.path("error").asString(""));

                                        code = safeErrorValue(
                                                        body.path("code").asString(""));

                                        blockedBy = safeErrorValue(
                                                        body.path("blocked_by").asString(""));

                                } catch (Exception ignored) {
                                        // La respuesta podría no ser JSON.
                                }

                                logger.warn(
                                                "Mercado Libre API: HTTP 403. " +
                                                                "path={}, error={}, code={}, blocked_by={}",
                                                path,
                                                error,
                                                code,
                                                blockedBy);

                                throw new ResponseStatusException(
                                                HttpStatus.FORBIDDEN,
                                                "Mercado Libre denegó el acceso al recurso.");
                        }

                        if (status == 404) {
                                throw new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Recurso no encontrado en Mercado Libre.");
                        }

                        if (status == 429) {
                                throw new ResponseStatusException(
                                                HttpStatus.SERVICE_UNAVAILABLE,
                                                "Mercado Libre limitó temporalmente las consultas.");
                        }

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "Mercado Libre devolvió un error HTTP " + status + ".");

                } catch (RestClientException exception) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_GATEWAY,
                                        "No se pudo comunicar con la API de Mercado Libre.");
                }
        }

        private String safeErrorValue(String value) {

                if (value == null ||
                                !value.matches("[A-Za-z0-9_ -]{1,80}")) {
                        return "unknown";
                }

                return value;
        }

        public MercadoLibreSellerLookupResponse findSellerByNickname(
                        String username,
                        String nickname) {

                if (nickname == null ||
                                !nickname.matches("[A-Za-z0-9_-]{2,64}")) {

                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Nickname inválido.");
                }

                String accessToken = oauthService.getAccessToken(username);

                JsonNode response = executeGet(
                                "/sites/MLA/search?nickname=" + nickname,
                                accessToken);

                JsonNode seller = response.path("seller");

                Long sellerId = nullableLong(seller, "id");

                if (sellerId != null && sellerId <= 0) {
                        sellerId = null;
                }

                return new MercadoLibreSellerLookupResponse(
                                nickname,
                                sellerId,
                                nullableString(seller, "nickname"));
        }

        public MercadoLibreUserResponse getPublicUser(
                        String username,
                        Long userId) {

                if (userId == null || userId <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Identificador de usuario inválido.");
                }

                String accessToken = oauthService.getAccessToken(username);

                JsonNode response = executeGet(
                                "/users/" + userId,
                                accessToken);

                JsonNode registrationNode = response.get("registration_date");

                System.out.println(
                                "Mercado Libre registration_date: " +
                                                (registrationNode == null
                                                                ? "CAMPO AUSENTE"
                                                                : registrationNode.toString()));

                JsonNode reputation = response.path("seller_reputation");

                return new MercadoLibreUserResponse(
                                nullableLong(response, "id"),
                                nullableString(response, "nickname"),
                                nullableString(response, "registration_date"),
                                nullableString(reputation, "level_id"),
                                nullableString(reputation, "power_seller_status"),
                                historicCompletedSales(reputation),
                                nullableString(reputation.path("transactions"), "period"));
        }

        // Solo aceptamos cantidad de transacciones completadas del período histórico.
        // No confundir con métricas de ventas de ventanas temporales reducidas.
        private Integer historicCompletedSales(JsonNode reputation) {
                JsonNode transactions = reputation.path("transactions");
                String period = nullableString(transactions, "period");

                if (!"historic".equalsIgnoreCase(period)) {
                        return null;
                }

                JsonNode completed = transactions.path("completed");
                if (!completed.isIntegralNumber() || !completed.canConvertToInt()) {
                        return null;
                }

                int number = completed.intValue();
                return number >= 0 ? number : null;
        }

        // ---------------------------------------
        // Lectura segura de atributos JSON
        // ---------------------------------------

        private String nullableString(
                        JsonNode node,
                        String field) {

                JsonNode value = node.path(field);

                if (value.isMissingNode() || value.isNull()) {
                        return null;
                }

                return value.asString();
        }

        private Long nullableLong(
                        JsonNode node,
                        String field) {

                JsonNode value = node.path(field);

                if (value.isMissingNode() || value.isNull()) {
                        return null;
                }

                return value.asLong();
        }
}