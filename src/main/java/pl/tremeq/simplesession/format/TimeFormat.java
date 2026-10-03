package pl.tremeq.simplesession.format;

/**
 * Converts a number of seconds into a human readable text.
 *
 * @author TremeQ
 */
public interface TimeFormat {

    /**
     * Formats a duration.
     *
     * @param totalSeconds Duration in seconds (negative values are treated as 0)
     * @return Formatted text
     */
    String format(long totalSeconds);
}
