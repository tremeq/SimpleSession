package pl.tremeq.simplesession.format;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeParserTest {

    @Test
    void parsesPlainSeconds() {
        assertEquals(3600, TimeParser.parse("3600"));
        assertEquals(0, TimeParser.parse("0"));
    }

    @Test
    void parsesUnits() {
        assertEquals(5400, TimeParser.parse("1h30m"));
        assertEquals(2 * 86400 + 5 * 3600, TimeParser.parse("2d 5h"));
        assertEquals(45, TimeParser.parse("45s"));
        assertEquals(604800 + 1, TimeParser.parse("1W1S"));
    }

    @Test
    void rejectsInvalidInput() {
        assertEquals(-1, TimeParser.parse(null));
        assertEquals(-1, TimeParser.parse(""));
        assertEquals(-1, TimeParser.parse("abc"));
        assertEquals(-1, TimeParser.parse("1x"));
        assertEquals(-1, TimeParser.parse("-5"));
        assertEquals(-1, TimeParser.parse("1h foo"));
        assertEquals(-1, TimeParser.parse("99999999999999999999"));
    }
}
