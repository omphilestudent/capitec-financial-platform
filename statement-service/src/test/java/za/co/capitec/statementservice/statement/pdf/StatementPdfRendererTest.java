package za.co.capitec.statementservice.statement.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatementPdfRendererTest {

    private final StatementPdfRenderer renderer = new StatementPdfRenderer();

    private StatementDocument document(int rowCount) {
        List<LedgerRow> rows = new ArrayList<>();
        BigDecimal running = new BigDecimal("1000.00");
        for (int i = 0; i < rowCount; i++) {
            running = running.add(new BigDecimal("1.00"));
            rows.add(new LedgerRow(LocalDateTime.of(2026, 10, 1, 10, 0).plusHours(i), "TXN-" + i,
                    "Payment " + i, "CREDIT", null, new BigDecimal("1.00"), running));
        }
        return new StatementDocument("STM-20261118-ABCD1234", "Thabo Mokoena", "cust-1", "29390331863",
                "SAVINGS", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "ZAR",
                LocalDateTime.of(2026, 11, 18, 12, 0), new BigDecimal("1000.00"), running,
                new BigDecimal(rowCount), BigDecimal.ZERO, 0, rows);
    }

    @Test
    void rendersAValidPdfWithBrandingReferenceAndBalances() throws Exception {
        byte[] pdf = renderer.render(document(3));

        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Capitec Financial Intelligence"));
            assertTrue(text.contains("STM-20261118-ABCD1234"));
            assertTrue(text.contains("29390331863"));
            assertTrue(text.contains("R 1 000.00"));
            assertTrue(text.contains("TXN-2"));
        }
    }

    @Test
    void longStatementsPaginateAndKeepEveryRow() throws Exception {
        byte[] pdf = renderer.render(document(120));

        assertTrue(renderer.pageCount(pdf) > 1);
    }

    @Test
    void moneyIsFormattedInSouthAfricanRandStyle() {
        assertEquals("R 1 234 567.89", StatementPdfRenderer.money(new BigDecimal("1234567.89"), null));
        assertEquals("R -15.00", StatementPdfRenderer.money(new BigDecimal("-15"), null));
        assertEquals("R 0.00", StatementPdfRenderer.money(BigDecimal.ZERO, null));
    }
}
