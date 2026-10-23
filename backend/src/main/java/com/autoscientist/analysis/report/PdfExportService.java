package com.autoscientist.analysis.report;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Markdown → PDF renderer with a bundled Unicode font (DejaVu Sans) and real
 * PDF tables. Inline Markdown markers are stripped, and known scientific
 * operators are transliterated so no literal markdown/LaTeX leaks into the PDF.
 */
@Slf4j
@Service
public class PdfExportService {

    private static final Color INK = new Color(30, 41, 59);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color BODY = new Color(51, 65, 85);
    private static final Color DANGER = new Color(153, 27, 27);
    private static final Color HEADER_BG = new Color(226, 232, 240);
    private static final Color BORDER = new Color(203, 213, 225);

    private static final Font TITLE_FONT = font(18, Font.BOLD, INK);
    private static final Font SUBTITLE_FONT = font(11, Font.ITALIC, MUTED);
    private static final Font SECTION_FONT = font(13, Font.BOLD, INK);
    private static final Font SUB_SECTION_FONT = font(11, Font.BOLD, INK);
    private static final Font BODY_FONT = font(10, Font.NORMAL, BODY);
    private static final Font BOLD_FONT = font(10, Font.BOLD, INK);
    private static final Font DISCLAIMER_FONT = font(9, Font.ITALIC, DANGER);
    private static final Font TABLE_HEADER_FONT = font(8, Font.BOLD, INK);
    private static final Font TABLE_BODY_FONT = font(8, Font.NORMAL, BODY);

    public byte[] generatePdfReport(String title, String markdownContent) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(document, baos);
            document.open();

            Paragraph header = new Paragraph("AUTOSCIENTIST AI", TITLE_FONT);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);

            Paragraph tagline = new Paragraph("From Raw Data to Evidence-Based Scientific Discovery", SUBTITLE_FONT);
            tagline.setAlignment(Element.ALIGN_CENTER);
            tagline.setSpacingAfter(15);
            document.add(tagline);

            PdfPTable divider = new PdfPTable(1);
            divider.setWidthPercentage(100);
            PdfPCell divCell = new PdfPCell();
            divCell.setBorder(Rectangle.BOTTOM);
            divCell.setBorderColor(BORDER);
            divCell.setBorderWidth(1.5f);
            divCell.setPadding(0);
            divider.addCell(divCell);
            document.add(divider);

            Paragraph reportTitle = new Paragraph(cleanInline(title), SECTION_FONT);
            reportTitle.setSpacingBefore(12);
            reportTitle.setSpacingAfter(12);
            document.add(reportTitle);

            List<String> tableBuffer = new ArrayList<>();
            for (String rawLine : markdownContent.split("\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    flushTable(document, tableBuffer);
                    continue;
                }
                if (line.startsWith("|") && line.endsWith("|")) {
                    tableBuffer.add(line);
                    continue;
                }
                flushTable(document, tableBuffer);

                if (line.startsWith("# ")) {
                    continue;
                } else if (line.startsWith("## ")) {
                    Paragraph section = new Paragraph(cleanInline(line.substring(3)), SECTION_FONT);
                    section.setSpacingBefore(12);
                    section.setSpacingAfter(6);
                    document.add(section);
                } else if (line.startsWith("### ") || line.startsWith("#### ")) {
                    int cut = line.startsWith("### ") ? 4 : 5;
                    Paragraph subSection = new Paragraph(cleanInline(line.substring(cut)), SUB_SECTION_FONT);
                    subSection.setSpacingBefore(8);
                    subSection.setSpacingAfter(4);
                    document.add(subSection);
                } else if (line.startsWith("> ")) {
                    Paragraph callout = new Paragraph(cleanInline(line.substring(2)), DISCLAIMER_FONT);
                    callout.setSpacingBefore(3);
                    callout.setSpacingAfter(3);
                    callout.setIndentationLeft(15);
                    document.add(callout);
                } else if (line.startsWith("- ")) {
                    Paragraph bullet = new Paragraph("• " + cleanInline(line.substring(2)), BODY_FONT);
                    bullet.setIndentationLeft(12);
                    bullet.setSpacingAfter(2);
                    document.add(bullet);
                } else if (line.matches("^\\d+\\.\\s+.*")) {
                    Paragraph numbered = new Paragraph(cleanInline(line), BODY_FONT);
                    numbered.setIndentationLeft(14);
                    numbered.setSpacingAfter(2);
                    document.add(numbered);
                } else if (line.startsWith("---")) {
                    Paragraph spacer = new Paragraph(" ", BODY_FONT);
                    spacer.setSpacingAfter(2);
                    document.add(spacer);
                } else {
                    Paragraph para = new Paragraph(cleanInline(line), BODY_FONT);
                    para.setSpacingAfter(4);
                    document.add(para);
                }
            }
            flushTable(document, tableBuffer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF report", e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private void flushTable(Document document, List<String> rows) {
        if (rows.isEmpty()) return;
        List<List<String>> parsed = new ArrayList<>();
        int columns = 0;
        for (String row : rows) {
            List<String> cells = new ArrayList<>();
            String inner = row.substring(1, row.length() - 1);
            for (String cell : inner.split("\\|", -1)) {
                cells.add(cleanInline(cell));
            }
            boolean separator = cells.stream().allMatch(c -> c.replace(":", "").replace("-", "").isBlank());
            if (separator) continue;
            columns = Math.max(columns, cells.size());
            parsed.add(cells);
        }
        rows.clear();
        if (parsed.isEmpty() || columns == 0) return;

        try {
            PdfPTable table = new PdfPTable(columns);
            table.setWidthPercentage(100);
            table.setSpacingBefore(4);
            table.setSpacingAfter(8);
            boolean header = true;
            for (List<String> cells : parsed) {
                for (int c = 0; c < columns; c++) {
                    String value = c < cells.size() ? cells.get(c) : "";
                    PdfPCell cell = new PdfPCell(new Phrase(value, header ? TABLE_HEADER_FONT : TABLE_BODY_FONT));
                    cell.setPadding(4);
                    cell.setBorderColor(BORDER);
                    if (header) cell.setBackgroundColor(HEADER_BG);
                    table.addCell(cell);
                }
                header = false;
            }
            document.add(table);
        } catch (DocumentException e) {
            log.warn("Skipping malformed markdown table in PDF: {}", e.getMessage());
        }
    }

    /** Removes markdown/LaTeX markers and normalises characters for safe rendering. */
    private String cleanInline(String raw) {
        if (raw == null) return "";
        String s = raw
                .replace("$H_0$", "H0").replace("$H_1$", "H1")
                .replace("$", "")
                .replace("`", "")
                .replace("**", "")
                .replace("★", "[BEST]")
                .replace("↔", " vs ")
                .replace("→", "->")
                .replace("≥", ">=")
                .replace("≤", "<=")
                .replace("²", "2")
                .replace("•", "-")
                .trim();
        // Remaining single-asterisk emphasis markers.
        s = s.replaceAll("(?<![\\w*])\\*(?=\\S)", "").replaceAll("(?<=\\S)\\*(?![\\w*])", "");
        return s;
    }

    private static Font font(float size, int style, Color color) {
        BaseFont base = FontRegistry.baseFont(style);
        if (base != null) {
            return new Font(base, size, style, color);
        }
        int fallbackStyle = style;
        return new Font(Font.HELVETICA, size, fallbackStyle, color);
    }

    /** Lazily loads the bundled DejaVu Sans family; falls back to Helvetica. */
    private static final class FontRegistry {
        private static BaseFont regular;
        private static BaseFont bold;
        private static BaseFont italic;
        private static boolean loaded;

        static synchronized BaseFont baseFont(int style) {
            if (!loaded) {
                loaded = true;
                regular = load("/fonts/DejaVuSans.ttf");
                bold = load("/fonts/DejaVuSans-Bold.ttf");
                italic = load("/fonts/DejaVuSans-Oblique.ttf");
            }
            if (style == Font.BOLD && bold != null) return bold;
            if (style == Font.ITALIC && italic != null) return italic;
            return regular;
        }

        private static BaseFont load(String resource) {
            try {
                return BaseFont.createFont(resource, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception e) {
                log.warn("Could not load bundled PDF font {} — falling back to Helvetica: {}", resource, e.getMessage());
                return null;
            }
        }
    }
}
