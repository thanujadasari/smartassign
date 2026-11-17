package com.smartassign.common;

import java.util.List;

/** Minimal RFC 4180 CSV builder with spreadsheet formula-injection protection. */
public final class CsvWriter {

    private final StringBuilder sb = new StringBuilder();

    public CsvWriter row(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(values.get(i)));
        }
        sb.append("\r\n");
        return this;
    }

    public String build() {
        return sb.toString();
    }

    static String escape(Object v) {
        if (v == null) return "";
        String s = v.toString();
        // neutralise values a spreadsheet would evaluate as a formula
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !(v instanceof Number)) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
