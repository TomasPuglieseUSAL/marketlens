package com.marketlens.backend.service;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Identifica productos Temu, no ofertas ni vendedores verificados. */
public final class TemuProductUrls {
    private static final Pattern PRODUCT = Pattern.compile("-g-(\\d{6,30})\\.html/?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile("\\d{6,30}");
    private TemuProductUrls() { }

    public static String parse(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null) return null;
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (!host.equals("temu.com") && !host.endsWith(".temu.com")) return null;
            String path = uri.getPath();
            if (path == null) return null;
            Matcher match = PRODUCT.matcher(path);
            if (match.find()) return match.group(1);
            if (!path.matches("(?i)^/goods\\.html/?$")) return null;
            String query = uri.getRawQuery();
            if (query == null) return null;
            for (String part : query.split("&")) {
                if (part.startsWith("goods_id=")) {
                    String id = part.substring("goods_id=".length());
                    if (ID.matcher(id).matches()) return id;
                }
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }
}
