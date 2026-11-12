package com.marketlens.backend.service;

import com.marketlens.backend.entity.Publication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comparacion heuristica y GENERICA basada en texto. Los resultados
 * PROVISIONAL_MATCH no certifican que dos ofertas sean identicas.
 * No hay reglas de marcas, modelos o categorias particulares.
 */
@Service
public class GenericProductMatchingService {

    // TF-IDF sirve para recuperar candidatos. Este es solo un piso de ruido.
    // Valores exploratorios: revisar con resultados de Postman antes de puntuar riesgo.
    private static final double MIN_SIMILARITY = 0.08;
    private static final double MIN_TOKEN_COVERAGE = 0.55;
    private static final int MIN_SHARED_IDENTIFYING_TOKENS = 2;
    private static final int DESCRIPTION_PREFIX_LENGTH = 350;

    // La abreviatura "1t" se interpreta heurísticamente como 1 TB.
    // Podria ser ambigua en algunas categorias; siempre exige coincidencia de titulo.
    private static final Pattern CAPACITY = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d+(?:[.,]\\d+)?)\\s*(gb|tb|t)(?![\\p{L}\\p{N}])");
    private static final Pattern LETTER_NUMBER_BOUNDARY = Pattern.compile(
            "(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})");
    private static final Pattern BUNDLE = Pattern.compile(
            "\\b(combo|bundle|pack|kit|lote)\\b");
    private static final Pattern WITHOUT_BOX = Pattern.compile(
            "\\bsin (?:su )?caja\\b");
    private static final Pattern WITH_BOX = Pattern.compile(
            "\\b(con (?:su )?caja|en caja|caja incluida)\\b");
    private static final Pattern USED = Pattern.compile(
            "\\b(usad[oa]s?|segunda mano|como nuev[oa]|preowned)\\b");
    private static final Pattern REFURBISHED = Pattern.compile(
            "\\b(reacondicionad[oa]s?|refurbished)\\b");
    private static final Pattern NEW = Pattern.compile(
            "\\b(nuev[oa]s?|sellad[oa]s?|new|sealed)\\b");

    // Terminos editoriales genericos; se conservan marca/modelo/variante.
    private static final Set<String> STOP_WORDS = Set.of(
            "de", "del", "la", "el", "los", "las", "un", "una",
            "para", "por", "en", "y", "venta", "vendo", "como", "color",
            "nuevo", "nueva", "nuevos", "nuevas", "new",
            "sellado", "sellada", "sellados", "selladas", "sealed",
            "usado", "usada", "usados", "usadas", "preowned",
            "reacondicionado", "reacondicionada",
            "reacondicionados", "reacondicionadas", "refurbished");

    public record MatchDecision(String status, String reason) {}

    private enum Condition { NEW, USED, REFURBISHED, UNKNOWN }
    private enum Packaging { WITH_BOX, WITHOUT_BOX, UNKNOWN, CONFLICT }

    public MatchDecision compare(Publication target, Publication candidate, double similarity) {
        if (!Double.isFinite(similarity) || similarity < MIN_SIMILARITY) {
            return insufficient("Similitud TF-IDF demasiado baja para comparar.");
        }

        String titleA = normalize(target.getTitle());
        String titleB = normalize(candidate.getTitle());
        if (titleA.isBlank() || titleB.isBlank()) {
            return insufficient("Falta el titulo de alguna publicacion.");
        }

        // Composiciones de paquetes desconocidas pueden sesgar el precio.
        if (hasBundleSignals(titleA) || hasBundleSignals(titleB)) {
            return insufficient("Al menos un titulo declara un combo o paquete cuya composicion no esta clara.");
        }

        Condition conditionA = condition(titleA);
        Condition conditionB = condition(titleB);
        if (conditionA == Condition.UNKNOWN || conditionB == Condition.UNKNOWN) {
            return insufficient("No se pudo establecer el estado de ambos productos.");
        }
        if (conditionA != conditionB) {
            return notComparable("Los estados declarados (nuevo, usado o reacondicionado) difieren.");
        }

        Set<String> capacitiesA = capacities(target);
        Set<String> capacitiesB = capacities(candidate);
        // Si falta una capacidad, NO se rechaza automaticamente.
        if (!capacitiesA.isEmpty() && !capacitiesB.isEmpty()
                && !capacitiesA.equals(capacitiesB)) {
            if (disjoint(capacitiesA, capacitiesB)) {
                return notComparable("Las capacidades declaradas son distintas.");
            }
            return insufficient("Hay varias capacidades y no se puede comprobar su equivalencia.");
        }

        Packaging packagingA = detectPackaging(normalize(target.getTitle())
                + " " + normalize(target.getDescription()));
        Packaging packagingB = detectPackaging(normalize(candidate.getTitle())
                + " " + normalize(candidate.getDescription()));
        if (packagingA == Packaging.CONFLICT || packagingB == Packaging.CONFLICT) {
            return insufficient("Hay declaraciones contradictorias sobre la caja original.");
        }
        // Solo rechazar la caja cuando AMBOS anuncian situaciones opuestas.
        if (packagingA != Packaging.UNKNOWN && packagingB != Packaging.UNKNOWN
                && packagingA != packagingB) {
            return notComparable("Una oferta declara caja y la otra declara expresamente que no la incluye.");
        }

        List<String> wordsA = identityTokens(titleA);
        List<String> wordsB = identityTokens(titleB);
        Set<String> uniqueA = new LinkedHashSet<>(wordsA);
        Set<String> uniqueB = new LinkedHashSet<>(wordsB);
        Set<String> shared = new HashSet<>(uniqueA);
        shared.retainAll(uniqueB);

        if (shared.size() < MIN_SHARED_IDENTIFYING_TOKENS) {
            return insufficient("No hay suficientes terminos identificativos compartidos.");
        }

        double coverageA = (double) shared.size() / uniqueA.size();
        double coverageB = (double) shared.size() / uniqueB.size();
        if (Math.min(coverageA, coverageB) < MIN_TOKEN_COVERAGE) {
            return insufficient("La coincidencia de terminos identificativos es demasiado baja.");
        }

        // Descartar numeros de modelo completamente incompatibles
        // si ambas publicaciones los especifican.
        Set<String> numericA = numericIdentifiers(wordsA);
        Set<String> numericB = numericIdentifiers(wordsB);
        if (!numericA.isEmpty() && !numericB.isEmpty() && disjoint(numericA, numericB)) {
            return insufficient("Los identificadores numericos declarados no coinciden.");
        }

        // Las cantidades asociadas a los mismos objetos no pueden contradecirse.
        if (hasConflictingQuantities(wordsA, wordsB)) {
            return insufficient("Las cantidades de los componentes no coinciden.");
        }

        // Evitar confundir terminos de variante mutuamente sustituidos:
        // 'modelo compacto' vs 'modelo profesional', sin codificar variantes.
        Set<String> onlyA = new HashSet<>(uniqueA);
        onlyA.removeAll(shared);
        Set<String> onlyB = new HashSet<>(uniqueB);
        onlyB.removeAll(shared);
        // Una capacidad informada en la descripcion del otro producto
        // no es una variante textual contradictoria.
        onlyA.removeIf(token -> token.startsWith("capacity_")
                && capacitiesB.contains(token.substring("capacity_".length())));
        onlyB.removeIf(token -> token.startsWith("capacity_")
                && capacitiesA.contains(token.substring("capacity_".length())));
        if (onlyA.size() == 1 && onlyB.size() == 1) {
            return insufficient("Cada publicacion especifica un descriptor diferente que podria ser una variante.");
        }

        // Misma bolsa de palabras, pero codigos o cantidades invertidos.
        if (frequencies(wordsA).equals(frequencies(wordsB))
                && !wordsA.equals(wordsB)
                && (numericIdentifiers(wordsA).size() >= 2
                    || hasMixedAlphanumericIdentifier(titleA)
                    || hasMixedAlphanumericIdentifier(titleB))) {
            return insufficient("Se invirtio el orden de codigos o cantidades potencialmente relevantes.");
        }

        return new MatchDecision("PROVISIONAL_MATCH",
                "Coincidencia textual parcial suficiente y sin contradicciones explicitas "
                + "de estado o capacidad. Equivalencia comercial no verificada.");
    }

    private List<String> identityTokens(String normalizedTitle) {
        String withCapacities = canonicalizeCapacities(normalizedTitle);
        List<String> result = new ArrayList<>();
        for (String chunk : withCapacities.split("[^\\p{L}\\p{N}_]+")) {
            if (chunk.isBlank()) continue;
            if (chunk.startsWith("capacity_") && chunk.length() > 9) {
                result.add(chunk);
                continue;
            }
            String separated = LETTER_NUMBER_BOUNDARY.matcher(chunk).replaceAll(" ");
            for (String token : separated.split(" ")) {
                if (!token.isBlank() && !STOP_WORDS.contains(token)) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    private String canonicalizeCapacities(String text) {
        return CAPACITY.matcher(text).replaceAll(match -> " capacity_"
                + capacityLabel(match.group(1), match.group(2)) + " ");
    }

    private Set<String> capacities(Publication publication) {
        Set<String> fromTitle = extractCapacities(normalize(publication.getTitle()));
        if (!fromTitle.isEmpty()) return fromTitle;
        String description = normalize(publication.getDescription());
        return extractCapacities(description.substring(0,
                Math.min(DESCRIPTION_PREFIX_LENGTH, description.length())));
    }

    private Set<String> extractCapacities(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = CAPACITY.matcher(text);
        while (matcher.find()) {
            result.add(capacityLabel(matcher.group(1), matcher.group(2)));
        }
        return result;
    }

    private String capacityLabel(String number, String unit) {
        BigDecimal gb = new BigDecimal(number.replace(',', '.'));
        if ("tb".equals(unit) || "t".equals(unit)) {
            gb = gb.multiply(BigDecimal.valueOf(1000));
        }
        return gb.stripTrailingZeros().toPlainString() + "gb";
    }

    private Map<String, Integer> frequencies(List<String> words) {
        Map<String, Integer> result = new HashMap<>();
        for (String word : words) result.merge(word, 1, Integer::sum);
        return result;
    }

    private Set<String> numericIdentifiers(List<String> words) {
        Set<String> result = new HashSet<>();
        for (String word : words) {
            if (word.matches("\\d+")) result.add(word);
        }
        return result;
    }

    private boolean hasConflictingQuantities(List<String> a, List<String> b) {
        Map<String, String> numbersA = quantitiesByFollowingToken(a);
        Map<String, String> numbersB = quantitiesByFollowingToken(b);
        for (Map.Entry<String, String> entry : numbersA.entrySet()) {
            String other = numbersB.get(entry.getKey());
            if (other != null && !entry.getValue().equals(other)) return true;
        }
        return false;
    }

    private Map<String, String> quantitiesByFollowingToken(List<String> words) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i + 1 < words.size(); i++) {
            if (words.get(i).matches("\\d+") && words.get(i + 1).matches("[\\p{L}]{3,}")) {
                result.putIfAbsent(words.get(i + 1), words.get(i));
            }
        }
        return result;
    }

    private boolean hasMixedAlphanumericIdentifier(String title) {
        String withoutCapacities = CAPACITY.matcher(title).replaceAll(" ");
        for (String token : withoutCapacities.split("[^\\p{L}\\p{N}]+")) {
            if (token.matches(".*[a-z].*") && token.matches(".*\\d.*")) return true;
        }
        return false;
    }

    private boolean disjoint(Set<String> a, Set<String> b) {
        for (String value : a) if (b.contains(value)) return false;
        return true;
    }

    private Condition condition(String title) {
        boolean used = USED.matcher(title).find();
        boolean refurbished = REFURBISHED.matcher(title).find();
        boolean fresh = NEW.matcher(title).find();
        if (title.matches(".*\\bcomo nuev[oa]\\b.*")) fresh = false;
        int detected = (used ? 1 : 0) + (refurbished ? 1 : 0) + (fresh ? 1 : 0);
        if (detected != 1) return Condition.UNKNOWN;
        return refurbished ? Condition.REFURBISHED : used ? Condition.USED : Condition.NEW;
    }

    private Packaging detectPackaging(String text) {
        boolean without = WITHOUT_BOX.matcher(text).find();
        boolean with = WITH_BOX.matcher(text).find();
        if (with && without) return Packaging.CONFLICT;
        if (!with && !without) return Packaging.UNKNOWN;
        return with ? Packaging.WITH_BOX : Packaging.WITHOUT_BOX;
    }

    private boolean hasBundleSignals(String title) {
        return title.contains("+") || BUNDLE.matcher(title).find();
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}+", "")
                .replaceAll("\\s+", " ").trim();
    }

    private MatchDecision insufficient(String reason) {
        return new MatchDecision("INSUFFICIENT_DATA", reason);
    }

    private MatchDecision notComparable(String reason) {
        return new MatchDecision("NOT_COMPARABLE", reason);
    }
}
