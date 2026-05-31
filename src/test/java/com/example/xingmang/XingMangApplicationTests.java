package com.example.xingmang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class XingMangApplicationTests {

    @Test
    void applicationEntryPointCanBeLoaded() {
        assertDoesNotThrow(() -> Class.forName("com.example.xingmang.XingMangApplication"));
    }
}
