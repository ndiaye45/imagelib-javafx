package fr.ndiaye.imagelib.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagsTest {

    @Test
    void normalizeTrimsAndRemovesLeadingHash() {
        assertEquals("plage de sable", Tags.normalize("  #plage   de  sable "));
    }

    @Test
    void normalizeRejectsEmptyTooLongAndCommaTags() {
        assertThrows(IllegalArgumentException.class, () -> Tags.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> Tags.normalize("#"));
        assertThrows(IllegalArgumentException.class, () -> Tags.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> Tags.normalize("a".repeat(Tags.MAX_LENGTH + 1)));
        assertThrows(IllegalArgumentException.class, () -> Tags.normalize("plage, été"));
    }

    @Test
    void foldIgnoresCaseAndAccents() {
        assertEquals("ete", Tags.fold("Été"));
        assertEquals("francais", Tags.fold("FRANÇAIS"));
    }

    @Test
    void termsSplitOnSpacesAndCommas() {
        assertEquals(List.of("plage", "ete", "famille"), Tags.terms("  Plage, été   famille "));
        assertEquals(List.of(), Tags.terms("  "));
        assertEquals(List.of(), Tags.terms(null));
    }
}
