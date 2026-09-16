package com.skystream.ssmusic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/** Plain-text retention; callers serialize all access, including crash writes. */
final class ReactiveLogFile {
    static final int MAX_ENTRIES = 100;
    static final int MAX_ENTRY_CHARS = 16 * 1024;
    static final String TRUNCATED = "\n\t... entry truncated\n";
    static final String PENDING_SUFFIX = ".pending";

    private ReactiveLogFile() {
    }

    static void append(File file, String entry) throws IOException {
        Deque<String> entries = new ArrayDeque<>(MAX_ENTRIES);
        if (file.exists()) {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                StringBuilder current = new StringBuilder();
                boolean lineStart = true;
                boolean truncated = false;
                int character;
                while ((character = input.read()) != -1) {
                    // LogFormat indents every stack-trace line, even multiline exceptions.
                    if (lineStart && character != '\t' && character != '\r' && character != '\n') {
                        if (current.length() > 0) {
                            retain(entries, finish(current, truncated));
                            current.setLength(0);
                            truncated = false;
                        }
                    }
                    if (current.length() < MAX_ENTRY_CHARS) {
                        current.append((char) character);
                    } else {
                        truncated = true;
                    }
                    lineStart = character == '\n';
                }
                // A partially written final line is not a complete message.
                if (current.length() > 0 && lineStart) {
                    retain(entries, finish(current, truncated));
                }
            }
        }
        if (entry != null) {
            retain(entries, bound(entry));
        }
        File pending = new File(file.getPath() + PENDING_SUFFIX);
        try {
            try (Writer output = new OutputStreamWriter(
                    new FileOutputStream(pending), StandardCharsets.UTF_8)) {
                for (String retained : entries) {
                    output.write(retained);
                }
            }
            if (!pending.renameTo(file)) {
                throw new IOException("Unable to replace reactive log");
            }
        } finally {
            if (pending.exists() && !pending.delete()) {
                throw new IOException("Unable to remove pending reactive log");
            }
        }
    }

    static String bound(String entry) {
        if (entry.length() < MAX_ENTRY_CHARS
                || (entry.length() == MAX_ENTRY_CHARS && entry.endsWith("\n"))) {
            return entry.endsWith("\n") ? entry : entry + "\n";
        }
        return prefix(entry, MAX_ENTRY_CHARS - TRUNCATED.length()) + TRUNCATED;
    }

    static String prefix(String text, int limit) {
        int end = Math.min(text.length(), limit);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static String finish(StringBuilder entry, boolean truncated) {
        return truncated
                ? prefix(entry.toString(), MAX_ENTRY_CHARS - TRUNCATED.length()) + TRUNCATED
                : entry.toString();
    }

    private static void retain(Deque<String> entries, String entry) {
        if (entries.size() == MAX_ENTRIES) {
            entries.removeFirst();
        }
        entries.addLast(entry);
    }
}
