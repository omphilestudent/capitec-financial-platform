package za.co.capitec.aggregation.aggregation;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class VelocityCalculator {

    public record Peaks(long peakIn10Minutes, long peakInHour) {
    }

    public Peaks peaks(List<LocalDateTime> sortedTimestamps) {
        return new Peaks(maxInWindow(sortedTimestamps, 10), maxInWindow(sortedTimestamps, 60));
    }

    static long maxInWindow(List<LocalDateTime> sorted, long windowMinutes) {
        long best = 0;
        int start = 0;
        for (int end = 0; end < sorted.size(); end++) {
            while (sorted.get(start).isBefore(sorted.get(end).minus(windowMinutes, ChronoUnit.MINUTES))) {
                start++;
            }
            best = Math.max(best, end - start + 1);
        }
        return best;
    }
}
