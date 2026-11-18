package za.co.capitec.statementservice.statement.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class StatementPdfRenderer {

    private static final float MARGIN = 40f;
    private static final float ROW_HEIGHT = 14f;
    private static final float BOTTOM = 60f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private static final byte[] LOGO = loadLogo();

    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static byte[] loadLogo() {
        try (var in = StatementPdfRenderer.class.getResourceAsStream("/branding/capitec-logo.png")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    public byte[] render(StatementDocument doc) throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDImageXObject logo = LOGO == null ? null : PDImageXObject.createFromByteArray(pdf, LOGO, "capitec-logo");
            PDPage page = newPage(pdf);
            PDPageContentStream cs = new PDPageContentStream(pdf, page);
            float top = PDRectangle.A4.getHeight() - MARGIN;

            top = header(cs, doc, top, logo);
            top = summary(cs, doc, top);
            top = tableHeader(cs, top);

            for (LedgerRow row : doc.rows()) {
                if (top - ROW_HEIGHT < BOTTOM) {
                    cs.close();
                    page = newPage(pdf);
                    cs = new PDPageContentStream(pdf, page);
                    top = PDRectangle.A4.getHeight() - MARGIN;
                    top = tableHeader(cs, top);
                }
                top = tableRow(cs, row, top);
            }

            if (doc.rows().isEmpty()) {
                text(cs, regular, 9, MARGIN, top - 14, "No transactions were posted in this period.");
            }
            cs.close();

            footers(pdf, doc);
            pdf.save(out);
            return out.toByteArray();
        }
    }

    public int pageCount(byte[] pdfBytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {
            return pdf.getNumberOfPages();
        }
    }

    private PDPage newPage(PDDocument pdf) {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        return page;
    }

    private float header(PDPageContentStream cs, StatementDocument doc, float top, PDImageXObject logo) throws IOException {
        if (logo != null) {
            float logoHeight = 26f;
            float logoWidth = logoHeight * logo.getWidth() / logo.getHeight();
            cs.drawImage(logo, MARGIN, top - logoHeight, logoWidth, logoHeight);
        } else {
            text(cs, bold, 16, MARGIN, top - 16, "Capitec");
        }
        text(cs, bold, 12, MARGIN, top - 34, "Main Account Statement");
        float right = PDRectangle.A4.getWidth() - MARGIN;
        textRight(cs, regular, 9, right, top - 16, "Statement reference: " + doc.statementReference());
        textRight(cs, regular, 9, right, top - 28, "Generated: " + doc.generatedAt().format(DATE_TIME));

        float y = top - 52;
        line(cs, MARGIN, y, PDRectangle.A4.getWidth() - MARGIN, y);
        y -= 16;
        field(cs, MARGIN, y, "Customer", doc.customerName());
        field(cs, MARGIN + 260, y, "Customer ID", doc.customerId());
        y -= 30;
        field(cs, MARGIN, y, "Account number", doc.accountNumber());
        field(cs, MARGIN + 260, y, "Account type", titleCase(doc.accountType()));
        y -= 30;
        field(cs, MARGIN, y, "Statement period", DATE.format(doc.periodStart()) + " to " + DATE.format(doc.periodEnd()));
        field(cs, MARGIN + 260, y, "Currency", doc.currency());
        return y - 18;
    }

    private float summary(PDPageContentStream cs, StatementDocument doc, float top) throws IOException {
        float y = top;
        float boxWidth = (PDRectangle.A4.getWidth() - 2 * MARGIN) / 4;
        String[][] cells = {
                {"Opening balance", money(doc.openingBalance(), null)},
                {"Total credits", money(doc.totalCredits(), null)},
                {"Total debits", money(doc.totalDebits(), null)},
                {"Closing balance", money(doc.closingBalance(), null)}
        };
        for (int i = 0; i < cells.length; i++) {
            float x = MARGIN + i * boxWidth;
            text(cs, regular, 8, x, y - 10, cells[i][0]);
            text(cs, bold, 11, x, y - 26, cells[i][1]);
        }
        y -= 40;
        text(cs, regular, 7, MARGIN, y, "Balances are derived from the transaction ledger: the opening balance is the sum of all posted credits");
        text(cs, regular, 7, MARGIN, y - 9, "less debits before the period start. FAILED transactions and transactions under review are excluded from this statement and its balances.");
        return y - 24;
    }

    private float tableHeader(PDPageContentStream cs, float top) throws IOException {
        line(cs, MARGIN, top, PDRectangle.A4.getWidth() - MARGIN, top);
        float y = top - 12;
        text(cs, bold, 8, col(0), y, "Date");
        text(cs, bold, 8, col(1), y, "Reference");
        text(cs, bold, 8, col(2), y, "Description");
        textRight(cs, bold, 8, col(4), y, "Debit");
        textRight(cs, bold, 8, col(5), y, "Credit");
        textRight(cs, bold, 8, col(6), y, "Balance");
        line(cs, MARGIN, y - 4, PDRectangle.A4.getWidth() - MARGIN, y - 4);
        return y - 6;
    }

    private float tableRow(PDPageContentStream cs, LedgerRow row, float top) throws IOException {
        float y = top - ROW_HEIGHT + 3;
        text(cs, regular, 8, col(0), y, DATE.format(row.createdAt().toLocalDate()));
        text(cs, regular, 8, col(1), y, clip(row.reference(), 36));
        text(cs, regular, 8, col(2), y, clip(row.description() == null ? "" : row.description(), 24));
        if (row.debit() != null) {
            textRight(cs, regular, 8, col(4), y, money(row.debit(), null));
        }
        if (row.credit() != null) {
            textRight(cs, regular, 8, col(5), y, money(row.credit(), null));
        }
        textRight(cs, regular, 8, col(6), y, money(row.runningBalance(), null));
        return top - ROW_HEIGHT;
    }

    private void footers(PDDocument pdf, StatementDocument doc) throws IOException {
        int total = pdf.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page = pdf.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                float right = PDRectangle.A4.getWidth() - MARGIN;
                line(cs, MARGIN, 40, right, 40);
                text(cs, regular, 7, MARGIN, 28, "Capitec  |  " + doc.statementReference()
                        + "  |  Confidential: issued to the account holder only");
                textRight(cs, regular, 7, right, 28, "Page " + (i + 1) + " of " + total);
            }
        }
    }

    private void field(PDPageContentStream cs, float x, float y, String label, String value) throws IOException {
        text(cs, regular, 7, x, y + 8, label.toUpperCase(Locale.ENGLISH));
        text(cs, regular, 9, x, y - 3, clip(value == null ? "" : value, 60));
    }

    private static float col(int index) {
        float[] x = {MARGIN, MARGIN + 65, MARGIN + 250, MARGIN + 380, MARGIN + 415, MARGIN + 460, MARGIN + 515};
        return x[index];
    }

    private void line(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws IOException {
        cs.setLineWidth(0.5f);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(ascii(value));
        cs.endText();
    }

    private void textRight(PDPageContentStream cs, PDType1Font font, float size, float xRight, float y, String value)
            throws IOException {
        float width = font.getStringWidth(ascii(value)) / 1000f * size;
        text(cs, font, size, xRight - width, y, value);
    }

    static String money(BigDecimal amount, String currency) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        boolean negative = scaled.signum() < 0;
        String plain = scaled.abs().toPlainString();
        String intPart = plain.substring(0, plain.indexOf('.'));
        String fraction = plain.substring(plain.indexOf('.') + 1);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < intPart.length(); i++) {
            if (i > 0 && (intPart.length() - i) % 3 == 0) {
                grouped.append(' ');
            }
            grouped.append(intPart.charAt(i));
        }
        String prefix = currency == null ? "R " : currency + " ";
        return prefix + (negative ? "-" : "") + grouped + "." + fraction;
    }

    private static String ascii(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            out.append(c >= 32 && c < 127 ? c : '?');
        }
        return out.toString();
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "~";
    }

    private static String titleCase(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ENGLISH);
    }
}
