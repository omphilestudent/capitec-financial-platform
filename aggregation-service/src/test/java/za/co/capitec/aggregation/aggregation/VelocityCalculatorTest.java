package za.co.capitec.aggregation.aggregation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VelocityCalculatorTest {

    private final VelocityCalculator calculator = new VelocityCalculator();

    @Test
    void peakCountsTheDensestTenMinuteWindow() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 12, 0);
        List<LocalDateTime> times = List.of(
                base, base.plusMinutes(1), base.plusMinutes(2), base.plusMinutes(3), base.plusMinutes(4),
                base.plusMinutes(5), base.plusHours(3), base.plusHours(5));

        VelocityCalculator.Peaks peaks = calculator.peaks(times);

        assertEquals(6, peaks.peakIn10Minutes());
        assertEquals(6, peaks.peakInHour());
    }

    @Test
    void emptyHistoryHasZeroPeaks() {
        VelocityCalculator.Peaks peaks = calculator.peaks(List.of());
        assertEquals(0, peaks.peakIn10Minutes());
        assertEquals(0, peaks.peakInHour());
    }

    @Test
    void windowBoundaryIsInclusiveOfTheWindowLength() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 1, 12, 0);
        assertEquals(2, VelocityCalculator.maxInWindow(List.of(base, base.plusMinutes(10)), 10));
        assertEquals(1, VelocityCalculator.maxInWindow(List.of(base, base.plusMinutes(11)), 10));
    }
}
