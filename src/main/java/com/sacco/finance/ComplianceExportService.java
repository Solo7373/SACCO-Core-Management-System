package com.sacco.finance;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sacco.audit.dto.AuditEventResponse;
import com.sacco.finance.dto.TaxBreakdownResponse;
import com.sacco.finance.dto.TaxBreakdownRow;
import com.sacco.finance.dto.TransactionSheetResponse;
import com.sacco.finance.dto.TransactionSheetRow;
import com.sacco.finance.export.SimpleXlsxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Auditor export engine. Every artifact is produced in three formats:
 *
 * <ul>
 *   <li>raw CSV for spreadsheet ingestion and machine diffing,</li>
 *   <li>XLSX for finance teams (dependency-free writer),</li>
 *   <li>sealed PDF for the audit file, carrying a SHA-256 document seal that
 *       can be reproduced from the CSV data.</li>
 * </ul>
 *
 * All reads go through {@link ComplianceService}, which enforces
 * COMPLIANCE_VIEW, so the export surface inherits the auditor-only RBAC gate.
 */
@Service
@RequiredArgsConstructor
public class ComplianceExportService {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    private final ComplianceService complianceService;

    public ExportFile exportTransactions(LocalDate fromDate, LocalDate toDate,
            String transactionType, String channel, String format, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        String normalized = normalizeFormat(format);
        TransactionSheetResponse sheet = complianceService.getTransactionSheet(
                fromDate, toDate, transactionType, channel, actorId);
        String base = "transaction-sheet-" + LocalDate.now().format(FILE_DATE);
        return switch (normalized) {
            case "CSV" -> new ExportFile(base + ".csv", CSV_CONTENT_TYPE,
                    csvTransactions(sheet).getBytes(StandardCharsets.UTF_8));
            case "XLSX" -> new ExportFile(base + ".xlsx", XLSX_CONTENT_TYPE, xlsxTransactions(sheet));
            default -> new ExportFile(base + ".pdf", PDF_CONTENT_TYPE, pdfTransactions(sheet));
        };
    }

    public ExportFile exportTaxBreakdown(LocalDate fromDate, LocalDate toDate,
            String format, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        String normalized = normalizeFormat(format);
        TaxBreakdownResponse breakdown = complianceService.getTaxBreakdown(fromDate, toDate, actorId);
        String base = "tax-breakdown-" + LocalDate.now().format(FILE_DATE);
        return switch (normalized) {
            case "CSV" -> new ExportFile(base + ".csv", CSV_CONTENT_TYPE,
                    csvTaxBreakdown(breakdown).getBytes(StandardCharsets.UTF_8));
            case "XLSX" -> new ExportFile(base + ".xlsx", XLSX_CONTENT_TYPE, xlsxTaxBreakdown(breakdown));
            default -> new ExportFile(base + ".pdf", PDF_CONTENT_TYPE, pdfTaxBreakdown(breakdown));
        };
    }

    public ExportFile exportAuditLog(int limit, String action, String entityType,
            String format, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        String normalized = normalizeFormat(format);
        List<AuditEventResponse> events = complianceService.getAuditLog(limit, action, entityType, actorId);
        String base = "audit-log-" + LocalDate.now().format(FILE_DATE);
        return switch (normalized) {
            case "CSV" -> new ExportFile(base + ".csv", CSV_CONTENT_TYPE,
                    csvAuditLog(events).getBytes(StandardCharsets.UTF_8));
            case "XLSX" -> new ExportFile(base + ".xlsx", XLSX_CONTENT_TYPE, xlsxAuditLog(events));
            default -> new ExportFile(base + ".pdf", PDF_CONTENT_TYPE, pdfAuditLog(events));
        };
    }

    private String normalizeFormat(String format) {
        String normalized = format == null || format.isBlank()
                ? "CSV"
                : format.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CSV", "XLSX", "PDF").contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported export format '" + format + "'. Use csv, xlsx or pdf.");
        }
        return normalized;
    }

    // ------------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------------

    private String csvTransactions(TransactionSheetResponse sheet) {
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Reference,Source,Transaction Type,Channel,Member,Gross Amount,Fee Amount,Tax Amount,Net Amount,Description\n");
        for (TransactionSheetRow row : sheet.getRows()) {
            csv.append(csvValue(row.getDate())).append(',')
                    .append(csvValue(row.getReference())).append(',')
                    .append(csvValue(row.getSource())).append(',')
                    .append(csvValue(row.getTransactionType())).append(',')
                    .append(csvValue(row.getChannel())).append(',')
                    .append(csvValue(row.getMemberName())).append(',')
                    .append(plain(row.getGrossAmount())).append(',')
                    .append(plain(row.getFeeAmount())).append(',')
                    .append(plain(row.getTaxAmount())).append(',')
                    .append(plain(row.getNetAmount())).append(',')
                    .append(csvValue(row.getDescription())).append('\n');
        }
        csv.append("TOTALS,,,,,,")
                .append(plain(sheet.getTotalGross())).append(',')
                .append(plain(sheet.getTotalFees())).append(',')
                .append(plain(sheet.getTotalTaxes())).append(',')
                .append(plain(sheet.getTotalNet())).append(",\n");
        return csv.toString();
    }

    private String csvTaxBreakdown(TaxBreakdownResponse breakdown) {
        StringBuilder csv = new StringBuilder();
        csv.append("Tax Name,Tax Code,Category,Rate %,Taxable Amount,Tax Collected,Transactions\n");
        for (TaxBreakdownRow row : breakdown.getRows()) {
            csv.append(csvValue(row.getTaxName())).append(',')
                    .append(csvValue(row.getTaxCode())).append(',')
                    .append(csvValue(row.getCategory())).append(',')
                    .append(plain(row.getRatePercent())).append(',')
                    .append(plain(row.getTaxableAmount())).append(',')
                    .append(plain(row.getTaxCollected())).append(',')
                    .append(row.getTransactionCount()).append('\n');
        }
        csv.append("TOTALS,,,,")
                .append(plain(breakdown.getTotalTaxableAmount())).append(',')
                .append(plain(breakdown.getTotalTaxCollected())).append(",\n");
        return csv.toString();
    }

    private String csvAuditLog(List<AuditEventResponse> events) {
        StringBuilder csv = new StringBuilder();
        csv.append("Timestamp,Actor ID,Action,Entity Type,Entity ID,Reason,Correlation ID\n");
        for (AuditEventResponse event : events) {
            csv.append(csvValue(event.getTimestamp())).append(',')
                    .append(event.getActorId() == null ? "" : event.getActorId()).append(',')
                    .append(csvValue(event.getAction())).append(',')
                    .append(csvValue(event.getEntityType())).append(',')
                    .append(event.getEntityId() == null ? "" : event.getEntityId()).append(',')
                    .append(csvValue(event.getReason())).append(',')
                    .append(csvValue(event.getCorrelationId())).append('\n');
        }
        return csv.toString();
    }

    // ------------------------------------------------------------------
    // XLSX
    // ------------------------------------------------------------------

    private byte[] xlsxTransactions(TransactionSheetResponse sheet) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Date", "Reference", "Source", "Transaction Type", "Channel", "Member",
                "Gross Amount", "Fee Amount", "Tax Amount", "Net Amount", "Description"));
        for (TransactionSheetRow row : sheet.getRows()) {
            rows.add(Arrays.asList(
                    row.getDate(), row.getReference(), row.getSource(), row.getTransactionType(),
                    row.getChannel(), row.getMemberName(), row.getGrossAmount(), row.getFeeAmount(),
                    row.getTaxAmount(), row.getNetAmount(), row.getDescription()));
        }
        rows.add(Arrays.asList("TOTALS", null, null, null, null, null,
                sheet.getTotalGross(), sheet.getTotalFees(), sheet.getTotalTaxes(),
                sheet.getTotalNet(), null));
        return SimpleXlsxWriter.write("Transactions", rows);
    }

    private byte[] xlsxTaxBreakdown(TaxBreakdownResponse breakdown) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Tax Name", "Tax Code", "Category", "Rate %", "Taxable Amount",
                "Tax Collected", "Transactions"));
        for (TaxBreakdownRow row : breakdown.getRows()) {
            rows.add(Arrays.asList(row.getTaxName(), row.getTaxCode(), row.getCategory(),
                    row.getRatePercent(), row.getTaxableAmount(), row.getTaxCollected(),
                    row.getTransactionCount()));
        }
        rows.add(Arrays.asList("TOTALS", null, null, null,
                breakdown.getTotalTaxableAmount(), breakdown.getTotalTaxCollected(), null));
        return SimpleXlsxWriter.write("Tax Breakdown", rows);
    }

    private byte[] xlsxAuditLog(List<AuditEventResponse> events) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Timestamp", "Actor ID", "Action", "Entity Type", "Entity ID", "Reason",
                "Correlation ID"));
        for (AuditEventResponse event : events) {
            rows.add(Arrays.asList(event.getTimestamp(), event.getActorId(), event.getAction(),
                    event.getEntityType(), event.getEntityId(), event.getReason(),
                    event.getCorrelationId()));
        }
        return SimpleXlsxWriter.write("Audit Log", rows);
    }

    // ------------------------------------------------------------------
    // Sealed PDF
    // ------------------------------------------------------------------

    private byte[] pdfTransactions(TransactionSheetResponse sheet) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 28);
            PdfWriter.getInstance(document, out);
            document.open();
            Fonts fonts = fonts();
            addTitle(document, "Sealed Transaction Audit Sheet",
                    "Savings charges, loan repayments, share transactions and import procurement",
                    sheet.getCount() + " rows | Gross " + money(sheet.getTotalGross())
                            + " | Net " + money(sheet.getTotalNet()), fonts);

            PdfPTable table = table(new float[]{1.0f, 1.5f, 1.1f, 1.5f, 1.0f, 1.8f,
                    1.2f, 1.0f, 1.0f, 1.2f, 2.2f});
            addHeader(table, fonts.header, "Date", "Reference", "Source", "Type", "Channel",
                    "Member", "Gross", "Fee", "Tax", "Net", "Description");
            for (TransactionSheetRow row : sheet.getRows()) {
                addCell(table, fonts.body, row.getDate() == null ? "" : row.getDate().toString());
                addCell(table, fonts.body, row.getReference());
                addCell(table, fonts.body, row.getSource());
                addCell(table, fonts.body, row.getTransactionType());
                addCell(table, fonts.body, row.getChannel());
                addCell(table, fonts.body, row.getMemberName());
                addCell(table, fonts.body, plain(row.getGrossAmount()));
                addCell(table, fonts.body, plain(row.getFeeAmount()));
                addCell(table, fonts.body, plain(row.getTaxAmount()));
                addCell(table, fonts.body, plain(row.getNetAmount()));
                addCell(table, fonts.body, row.getDescription());
            }
            addTotalsRow(table, fonts.totals, "TOTALS", null, null, null, null, null,
                    plain(sheet.getTotalGross()), plain(sheet.getTotalFees()),
                    plain(sheet.getTotalTaxes()), plain(sheet.getTotalNet()), null);
            document.add(table);

            addSeal(document, csvTransactions(sheet), fonts.small);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate transaction audit PDF: " + ex.getMessage(), ex);
        }
    }

    private byte[] pdfTaxBreakdown(TaxBreakdownResponse breakdown) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 32, 32, 32, 28);
            PdfWriter.getInstance(document, out);
            document.open();
            Fonts fonts = fonts();
            addTitle(document, "Sealed Tax Breakdown",
                    "Indirect taxes collected on fees and import procurement",
                    breakdown.getRows().size() + " tax lines | Taxable " + money(breakdown.getTotalTaxableAmount())
                            + " | Collected " + money(breakdown.getTotalTaxCollected()), fonts);

            PdfPTable table = table(new float[]{2.2f, 1.4f, 1.2f, 0.9f, 1.5f, 1.5f, 1.0f});
            addHeader(table, fonts.header, "Tax Name", "Code", "Category", "Rate %",
                    "Taxable Amount", "Tax Collected", "Transactions");
            for (TaxBreakdownRow row : breakdown.getRows()) {
                addCell(table, fonts.body, row.getTaxName());
                addCell(table, fonts.body, row.getTaxCode());
                addCell(table, fonts.body, row.getCategory());
                addCell(table, fonts.body, plain(row.getRatePercent()));
                addCell(table, fonts.body, plain(row.getTaxableAmount()));
                addCell(table, fonts.body, plain(row.getTaxCollected()));
                addCell(table, fonts.body, String.valueOf(row.getTransactionCount()));
            }
            addTotalsRow(table, fonts.totals, "TOTALS", null, null, null,
                    plain(breakdown.getTotalTaxableAmount()), plain(breakdown.getTotalTaxCollected()), null);
            document.add(table);

            addSeal(document, csvTaxBreakdown(breakdown), fonts.small);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate tax breakdown PDF: " + ex.getMessage(), ex);
        }
    }

    private byte[] pdfAuditLog(List<AuditEventResponse> events) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 24, 24, 24, 28);
            PdfWriter.getInstance(document, out);
            document.open();
            Fonts fonts = fonts();
            addTitle(document, "Sealed Audit Trail",
                    "Every privileged action captured by the SCMS audit service",
                    events.size() + " events", fonts);

            PdfPTable table = table(new float[]{1.5f, 0.8f, 1.7f, 1.3f, 0.9f, 3.2f, 2.0f});
            addHeader(table, fonts.header, "Timestamp", "Actor", "Action", "Entity", "Entity ID",
                    "Reason", "Correlation ID");
            for (AuditEventResponse event : events) {
                addCell(table, fonts.body, event.getTimestamp() == null ? "" : DATETIME_FORMAT.format(event.getTimestamp()));
                addCell(table, fonts.body, event.getActorId() == null ? "" : String.valueOf(event.getActorId()));
                addCell(table, fonts.body, event.getAction());
                addCell(table, fonts.body, event.getEntityType());
                addCell(table, fonts.body, event.getEntityId() == null ? "" : String.valueOf(event.getEntityId()));
                addCell(table, fonts.body, event.getReason());
                addCell(table, fonts.body, event.getCorrelationId());
            }
            document.add(table);

            addSeal(document, csvAuditLog(events), fonts.small);
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate audit log PDF: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------
    // PDF helpers
    // ------------------------------------------------------------------

    private void addTitle(Document document, String title, String subtitle, String meta, Fonts fonts)
            throws Exception {
        Paragraph heading = new Paragraph("SACCO Core Management System", fonts.title);
        heading.setAlignment(Element.ALIGN_CENTER);
        document.add(heading);

        Paragraph subheading = new Paragraph(title, fonts.subtitle);
        subheading.setAlignment(Element.ALIGN_CENTER);
        document.add(subheading);

        Paragraph description = new Paragraph(subtitle, fonts.small);
        description.setAlignment(Element.ALIGN_CENTER);
        document.add(description);

        Paragraph generated = new Paragraph(meta + "   |   Generated: "
                + DATETIME_FORMAT.format(LocalDateTime.now()), fonts.body);
        generated.setSpacingBefore(8);
        generated.setSpacingAfter(10);
        document.add(generated);
    }

    private void addSeal(Document document, String canonicalContent, Font font) throws Exception {
        String digest = sha256(canonicalContent);
        Paragraph seal = new Paragraph("Document seal (SHA-256): " + digest, font);
        seal.setSpacingBefore(16);
        document.add(seal);
        Paragraph note = new Paragraph(
                "This sealed export is a tamper-evident record. Re-hashing the raw CSV export "
                        + "of the same dataset must reproduce the seal above. Generated by the "
                        + "SCMS Compliance Workspace on " + DATETIME_FORMAT.format(LocalDateTime.now()) + ".",
                font);
        document.add(note);
    }

    private Fonts fonts() {
        return new Fonts(
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(230, 81, 0)),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(191, 54, 12)),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE),
                FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.BLACK),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new Color(191, 54, 12)),
                FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.DARK_GRAY)
        );
    }

    private PdfPTable table(float[] widths) throws Exception {
        PdfPTable table = new PdfPTable(widths.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);
        table.setHeaderRows(1);
        return table;
    }

    private void addHeader(PdfPTable table, Font font, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setBackgroundColor(new Color(230, 81, 0));
            cell.setPadding(3.5f);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, Font font, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, font));
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void addTotalsRow(PdfPTable table, Font font, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, font));
            cell.setPadding(3.5f);
            cell.setBackgroundColor(new Color(255, 243, 224));
            table.addCell(cell);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compute export seal: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String plain(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String money(BigDecimal value) {
        return "UGX " + plain(value);
    }

    public record ExportFile(String fileName, String contentType, byte[] content) {
    }

    private record Fonts(Font title, Font subtitle, Font header, Font body, Font totals, Font small) {
    }
}
