package br.org.cesar.blueguide.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The number fields of the anchor editor and the calibration take a dot or a comma, as typed. */
class DecimalInputTest {

    @Test
    fun `decimal input accepts a dot or a comma as the separator`() {
        assertEquals(2.5, requireNotNull(DecimalInput.parse("2,5")), TOLERANCE)
        assertEquals(2.5, requireNotNull(DecimalInput.parse("2.5")), TOLERANCE)
        assertEquals(-1.25, requireNotNull(DecimalInput.parse("-1,25")), TOLERANCE)
        assertEquals(3.0, requireNotNull(DecimalInput.parse("+3")), TOLERANCE)
        assertEquals(0.5, requireNotNull(DecimalInput.parse(",5")), TOLERANCE)
        assertEquals(2.0, requireNotNull(DecimalInput.parse("2.")), TOLERANCE)
        assertEquals(4.0, requireNotNull(DecimalInput.parse("4")), TOLERANCE)
    }

    @Test
    fun `decimal input ignores surrounding spaces`() {
        assertEquals(1.5, requireNotNull(DecimalInput.parse("  1,5 ")), TOLERANCE)
    }

    @Test
    fun `decimal input refuses what is not a plain number`() {
        for (text in listOf("", " ", "-", ",", "abc", "1e3", "nan", "NaN", "inf", "Infinity", "1,2,3", "1.234,5", "1 5", "--1", "0x10")) {
            assertNull("'$text'", DecimalInput.parse(text))
        }
    }

    @Test
    fun `the text for a field is a plain decimal without exponent or trailing zeros`() {
        assertEquals("2.5", DecimalInput.text(2.5))
        assertEquals("-1", DecimalInput.text(-1.0))
        assertEquals("0.125", DecimalInput.text(0.125))
        assertEquals("0", DecimalInput.text(-0.0001))
        assertEquals("0.00001 has no exponent", "0", DecimalInput.text(0.00001))
        assertEquals("1234567", DecimalInput.text(1234567.0))
        assertEquals("", DecimalInput.text(Double.NaN))
    }

    @Test
    fun `what a field shows can be read back`() {
        for (value in listOf(2.5, -1.0, 0.125, 1234567.0, 0.001)) {
            assertEquals(value, requireNotNull(DecimalInput.parse(DecimalInput.text(value))), TOLERANCE)
        }
    }

    private companion object {
        const val TOLERANCE = 1e-6
    }
}
