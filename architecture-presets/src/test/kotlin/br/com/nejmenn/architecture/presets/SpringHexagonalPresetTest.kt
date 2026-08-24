package br.com.nejmenn.architecture.presets

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpringHexagonalPresetTest {
    @Test fun `preset contains the expected defaults`() {
        val preset = ArchitecturePresets.named("spring-hexagonal").configuration()
        assertEquals("Domain", preset.domainModelSuffix)
        assertEquals("UseCase", preset.forbiddenSuffixes["Service"])
        assertTrue("com.fasterxml.jackson" in preset.forbiddenSerialization)
    }
}
