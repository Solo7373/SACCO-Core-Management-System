package com.sacco.finance.export;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal, dependency-free Office Open XML workbook writer.
 *
 * <p>Produces a valid single-sheet .xlsx package using inline strings and a
 * small style table (orange header, two-decimal number format). This avoids
 * adding Apache POI to a project that already ships OpenPDF.</p>
 */
public final class SimpleXlsxWriter {

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
              <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
            </Types>
            """;

    private static final String ROOT_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
            """;

    private static final String WORKBOOK_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
              <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
            </Relationships>
            """;

    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <fonts count="2">
                <font><sz val="11"/><name val="Calibri"/></font>
                <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
              </fonts>
              <fills count="3">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="FFE65100"/><bgColor indexed="64"/></patternFill></fill>
              </fills>
              <borders count="1">
                <border><left/><right/><top/><bottom/><diagonal/></border>
              </borders>
              <cellStyleXfs count="1">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
              </cellStyleXfs>
              <cellXfs count="3">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
                <xf numFmtId="4" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
              </cellXfs>
            </styleSheet>
            """;

    private SimpleXlsxWriter() {
    }

    /**
     * Writes one worksheet. The first row is styled as a header; numeric values
     * are written as numbers and everything else as inline strings.
     */
    public static byte[] write(String sheetName, List<List<Object>> rows) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(out)) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "xl/workbook.xml", workbook(sheetName));
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", sheet(rows));
            zip.finish();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate XLSX workbook: " + ex.getMessage(), ex);
        }
    }

    private static void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String workbook(String sheetName) {
        String safeName = sanitizeSheetName(sheetName);
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
                """.formatted(escape(safeName));
    }

    private static String sheet(List<List<Object>> rows) {
        int columnCount = rows.stream().mapToInt(List::size).max().orElse(1);
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<sheetViews><sheetView workbookViewId=\"0\">");
        xml.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>");
        xml.append("</sheetView></sheetViews>");
        xml.append("<sheetFormatPr defaultRowHeight=\"15\"/>");
        xml.append("<cols>");
        for (int column = 0; column < columnCount; column++) {
            xml.append("<col min=\"").append(column + 1).append("\" max=\"").append(column + 1)
                    .append("\" width=\"").append(widthFor(rows, column)).append("\" customWidth=\"1\"/>");
        }
        xml.append("</cols>");
        xml.append("<sheetData>");

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<Object> row = rows.get(rowIndex);
            xml.append("<row r=\"").append(rowIndex + 1).append("\">");
            for (int column = 0; column < row.size(); column++) {
                appendCell(xml, rowIndex, column, row.get(column));
            }
            xml.append("</row>");
        }

        xml.append("</sheetData>");
        if (!rows.isEmpty()) {
            xml.append("<autoFilter ref=\"A1:").append(columnLetter(columnCount - 1)).append(rows.size()).append("\"/>");
        }
        xml.append("</worksheet>");
        return xml.toString();
    }

    private static void appendCell(StringBuilder xml, int rowIndex, int column, Object value) {
        String reference = columnLetter(column) + (rowIndex + 1);
        if (value instanceof Number number && !(value instanceof BigDecimal decimal && decimal.scale() < 0)) {
            xml.append("<c r=\"").append(reference).append("\" s=\"2\"><v>")
                    .append(value instanceof BigDecimal bigDecimal
                            ? bigDecimal.toPlainString()
                            : String.valueOf(number))
                    .append("</v></c>");
            return;
        }
        if (value instanceof Boolean bool) {
            xml.append("<c r=\"").append(reference).append("\" t=\"b\"><v>")
                    .append(bool ? '1' : '0').append("</v></c>");
            return;
        }
        String text = value == null ? "" : String.valueOf(value);
        xml.append("<c r=\"").append(reference).append("\"");
        if (rowIndex == 0) {
            xml.append(" s=\"1\"");
        }
        xml.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                .append(escape(text))
                .append("</t></is></c>");
    }

    private static double widthFor(List<List<Object>> rows, int column) {
        int longest = 8;
        for (List<Object> row : rows) {
            if (column < row.size()) {
                Object value = row.get(column);
                if (value != null) {
                    longest = Math.max(longest, Math.min(60, String.valueOf(value).length() + 2));
                }
            }
        }
        return Math.min(60, longest);
    }

    private static String columnLetter(int index) {
        StringBuilder letters = new StringBuilder();
        int current = index;
        do {
            letters.insert(0, (char) ('A' + current % 26));
            current = current / 26 - 1;
        } while (current >= 0);
        return letters.toString();
    }

    private static String sanitizeSheetName(String name) {
        String cleaned = name == null || name.isBlank() ? "Export" : name.trim();
        cleaned = cleaned.replaceAll("[\\\\/\\[\\]:*?]", " ").trim();
        if (cleaned.length() > 31) {
            cleaned = cleaned.substring(0, 31);
        }
        return cleaned.isBlank() ? "Export" : cleaned;
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
