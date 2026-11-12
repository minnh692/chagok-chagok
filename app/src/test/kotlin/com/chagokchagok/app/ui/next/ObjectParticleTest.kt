package com.chagokchagok.app.ui.next

import org.junit.Assert.assertEquals
import org.junit.Test

class ObjectParticleTest {
  @Test fun picksParticleByFinalConsonant() {
    assertEquals("를", objectParticle("중구"))
    assertEquals("을", objectParticle("양평군"))
    assertEquals("를", objectParticle("경주시"))
  }
}
