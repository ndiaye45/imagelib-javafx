package fr.ndiaye.imagelib.model;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Règles de normalisation et de comparaison des tags. */
public final class Tags {

    public static final int MAX_LENGTH = 40;

    private Tags() {
    }

    /**
     * Nettoie un tag saisi par l'utilisateur : espaces superflus et « # » initial retirés.
     *
     * @throws IllegalArgumentException si le tag est vide, trop long ou contient une virgule
     */
    public static String normalize(String raw) {
        String tag = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        while (tag.startsWith("#")) {
            tag = tag.substring(1).strip();
        }
        if (tag.isEmpty()) {
            throw new IllegalArgumentException("Le tag est vide");
        }
        if (tag.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Tag trop long (" + MAX_LENGTH + " caractères maximum)");
        }
        if (tag.indexOf(',') >= 0) {
            throw new IllegalArgumentException("Un tag ne peut pas contenir de virgule");
        }
        return tag;
    }

    /** Forme de comparaison : minuscules et sans accents (« Été » devient « ete »). */
    public static String fold(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Découpe une saisie de recherche (espaces ou virgules) en termes comparables. */
    public static List<String> terms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.split("[\\s,]+"))
                .map(Tags::fold)
                .filter(term -> !term.isEmpty())
                .toList();
    }
}
