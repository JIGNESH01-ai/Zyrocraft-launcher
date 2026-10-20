package net.kdt.pojavlaunch.progresskeeper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Session-only record of finished and failed launcher tasks,
 * displayed by the Downloads screen.
 */
public final class TaskHistory {
    public static final class Entry {
        /** String resource describing the task, or 0 when {@link #titleFallback} is used */
        public final int titleRes;
        public final String titleFallback;
        public final Object[] args;
        public final long timestamp;

        public Entry(int titleRes, String titleFallback, Object[] args, long timestamp) {
            this.titleRes = titleRes;
            this.titleFallback = titleFallback;
            this.args = args;
            this.timestamp = timestamp;
        }
    }

    private static final int MAX_ENTRIES = 64;
    private static final List<Entry> sCompleted = new ArrayList<>();
    private static final List<Entry> sFailed = new ArrayList<>();

    private TaskHistory() {}

    public static synchronized void recordCompleted(int titleRes, Object[] args) {
        sCompleted.add(new Entry(titleRes, null, args, System.currentTimeMillis()));
        trim(sCompleted);
    }

    public static synchronized void recordFailed(String message) {
        sFailed.add(new Entry(0, message, null, System.currentTimeMillis()));
        trim(sFailed);
    }

    private static void trim(List<Entry> list) {
        while (list.size() > MAX_ENTRIES) list.remove(0);
    }

    public static synchronized List<Entry> getCompleted() {
        List<Entry> copy = new ArrayList<>(sCompleted);
        Collections.reverse(copy);
        return copy;
    }

    public static synchronized List<Entry> getFailed() {
        List<Entry> copy = new ArrayList<>(sFailed);
        Collections.reverse(copy);
        return copy;
    }
}
