package com.marketlens.backend.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemuProductUrlsTest {
    @Test void recognizesProductUrls() {
        assertEquals("601099512345678", TemuProductUrls.parse("https://www.temu.com/es/producto-g-601099512345678.html"));
        assertEquals("601099512345678", TemuProductUrls.parse("https://www.temu.com/goods.html?goods_id=601099512345678&ref=foo"));
    }
    @Test void rejectsInvalidHostsAndNonProducts() {
        assertNull(TemuProductUrls.parse("https://temu.com.attacker.example/goods.html?goods_id=601099512345678"));
        assertNull(TemuProductUrls.parse("http://temu.com/goods.html?goods_id=601099512345678"));
        assertNull(TemuProductUrls.parse("https://www.temu.com/search_result.html"));
        assertNull(TemuProductUrls.parse("https://www.temu.com/goods.html?goods_id=bad"));
    }
}
