package com.smartassign.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void escapesQuotesCommasAndFormulaInjection() {
        String csv = new CsvWriter().row(Arrays.asList("a,b", "say \"hi\"", "=SUM(A1)", -5, null)).build();
        assertThat(csv).isEqualTo("\"a,b\",\"say \"\"hi\"\"\",'=SUM(A1),-5,\r\n");
    }

    @Test
    void emptyRowIsJustALineBreak() {
        assertThat(new CsvWriter().row(List.of()).build()).isEqualTo("\r\n");
    }
}
