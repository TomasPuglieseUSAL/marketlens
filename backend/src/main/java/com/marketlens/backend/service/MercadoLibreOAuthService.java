package com.marketlens.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MercadoLibreOAuthService {

    private static final String AUTHORIZATION_URL = "https://auth.mercadolibre.com.ar/authorization";

    private static final String TOKEN_URL = "https://api.mercadolibre.com/oauth/token";

    private static final Logger logger = LoggerFactory.getLogger(MercadoLibreOAuthService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final boolean pkceEnabled;

    // En modo demo se conserva el bean para no romper las dependencias,
    // pero las operaciones OAuth se rechazan antes de llamar a Mercado Libre.
    @Value("${mercadolibre.oauth.enabled:true}")
    private boolean enabled = true;

    private final SecureRandom random = new SecureRandom();

    private final RestClient restClient = RestClient.create();

    // Almacenamiento temporal para desarrollo.
    private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();

    private final Map<String, AuthorizedAccount> accounts = new ConcurrentHashMap<>();

    public MercadoLibreOAuthService(
            @Value("${mercadolibre.oauth.client-id}") String clientId,
            @Value("${mercadolibre.oauth.client-secret}") String clientSecret,
            @Value("${mercadolibre.oauth.redirect-uri}") String redirectUri,
            @Value("${mercadolibre.oauth.pkce-enabled:true}") boolean pkceEnabled) {

        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.pkceEnabled = pkceEnabled;
    }

    public String createAuthorizationUrl(String username) {

        requireOAuthEnabled();

        // Eliminar solicitudes vencidas.
        Instant now = Instant.now();

        pending.entrySet().removeIf(
                entry -> entry.getValue().expiresAt().isBefore(now));

        String state = randomUrlSafe(32);

        String verifier = pkceEnabled
                ? randomUrlSafe(64)
                : null;

        String challenge = pkceEnabled
                ? createChallenge(verifier)
                : null;

        pending.put(
                state,
                new PendingAuthorization(
                        username,
                        verifier,
                        Instant.now().plusSeconds(300)));

        var builder = UriComponentsBuilder
                .fromUriString(AUTHORIZATION_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state);

        if (pkceEnabled) {
            builder
                    .queryParam("code_challenge", challenge)
                    .queryParam("code_challenge_method", "S256");
        }

        return builder.encode().build().toUriString();
    }

    public void completeAuthorization(
            String code,
            String state) {

        requireOAuthEnabled();

        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Estado OAuth faltante.");
        }

        // El estado se consume una sola vez.
        PendingAuthorization request = pending.remove(state);

        if (request == null ||
                Instant.now().isAfter(request.expiresAt())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Autorización OAuth inválida o vencida.");
        }

        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Código de autorización faltante.");
        }
        logger.info("MELI OAuth: state validado correctamente.");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);

        if (pkceEnabled) {
            form.add("code_verifier", request.verifier());
        }

        JsonNode response;

        try {
            logger.info("MELI OAuth: iniciando intercambio de token.");
            response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

        } catch (RestClientResponseException exception) {

            String errorCode = "unknown";

            try {
                JsonNode errorResponse = objectMapper.readTree(
                        exception.getResponseBodyAsString());

                String candidate = errorResponse
                        .path("error")
                        .asString("");

                // Registrar solamente un código de error seguro.
                if (candidate.matches("[a-zA-Z0-9_-]{1,64}")) {
                    errorCode = candidate;
                }

            } catch (Exception ignored) {
                // La respuesta remota podría no contener JSON.
            }

            logger.warn(
                    "OAuth Mercado Libre: token endpoint devolvió HTTP {}. Código: {}",
                    exception.getStatusCode().value(),
                    errorCode);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Mercado Libre rechazó el intercambio del código OAuth.");

        } catch (RestClientException exception) {

            logger.error(
                    "OAuth Mercado Libre: error de comunicación. Tipo: {}",
                    exception.getClass().getSimpleName());

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo comunicar con el servicio OAuth de Mercado Libre.");
        }
        logger.info("MELI OAuth: intercambio HTTP completado.");

        if (response == null ||
                response.path("access_token").asString("").isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Mercado Libre no devolvió un access token válido.");
        }

        String accessToken = response.path("access_token").asString();

        String refreshToken = response.path("refresh_token").asString(null);

        Long externalUserId = response.hasNonNull("user_id")
                ? response.path("user_id").asLong()
                : null;

        long expiresIn = response.path("expires_in").asLong(21600);

        Instant expiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn));

        // Los tokens nunca se devuelven al navegador.
        accounts.put(
                request.username(),
                new AuthorizedAccount(
                        externalUserId,
                        accessToken,
                        refreshToken,
                        expiresAt));
        logger.info("MELI OAuth: autorización almacenada correctamente.");
    }

    public boolean isConnected(String username) {

        if (!enabled) return false;

        AuthorizedAccount account = accounts.get(username);

        return account != null
                && Instant.now().isBefore(account.expiresAt());
    }

    public Long getExternalUserId(String username) {

        if (!enabled) return null;

        AuthorizedAccount account = accounts.get(username);

        return account == null
                ? null
                : account.externalUserId();
    }

    public Instant getExpiresAt(String username) {

        if (!enabled) return null;

        AuthorizedAccount account = accounts.get(username);

        return account == null
                ? null
                : account.expiresAt();
    }

    // Lo utilizaremos en el futuro cliente de la API.
    public String getAccessToken(String username) {

        requireOAuthEnabled();

        if (!isConnected(username)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "No hay una autorización vigente de Mercado Libre.");
        }

        return accounts.get(username).accessToken();
    }


    private void requireOAuthEnabled() {
        if (!enabled) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La integracion OAuth de Mercado Libre esta deshabilitada en modo demo.");
        }
    }

    private String randomUrlSafe(int bytes) {

        byte[] buffer = new byte[bytes];

        random.nextBytes(buffer);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(buffer);
    }

    private String createChallenge(String verifier) {

        try {
            byte[] hash = MessageDigest
                    .getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 no está disponible.",
                    exception);
        }
    }

    private record PendingAuthorization(
            String username,
            String verifier,
            Instant expiresAt) {
    }

    private record AuthorizedAccount(
            Long externalUserId,
            String accessToken,
            String refreshToken,
            Instant expiresAt) {
    }
}