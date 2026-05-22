package com.codex.apikeychat;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

class OfficeProcessor {
    static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private static final int MAX_EXTRACT_CHARS = 60000;
    private static final int MAX_SHEETS = 8;
    private static final int MAX_ROWS_PER_SHEET = 160;
    private static final int MAX_CELLS_PER_ROW = 24;
    private static final int MAX_SLIDES = 60;
    private static final int MAX_PARAGRAPHS = 320;
    private static final int MAX_TABLE_ROWS = 120;

    private OfficeProcessor() {
    }

    static void initAndroidPoi() {
        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl");
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl");
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl");
    }

    static boolean isOfficeFile(String filename, String mimeType) {
        String ext = extension(filename);
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        return ".docx".equals(ext) || ".xlsx".equals(ext) || ".pptx".equals(ext)
                || MIME_DOCX.equals(mime) || MIME_XLSX.equals(mime) || MIME_PPTX.equals(mime);
    }

    static ExtractedOffice extract(String filename, String mimeType, byte[] bytes) throws Exception {
        String ext = extension(filename);
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (".docx".equals(ext) || MIME_DOCX.equals(mime)) {
            return extractDocx(filename, bytes);
        }
        if (".xlsx".equals(ext) || MIME_XLSX.equals(mime)) {
            return extractXlsx(filename, bytes);
        }
        if (".pptx".equals(ext) || MIME_PPTX.equals(mime)) {
            return extractPptx(filename, bytes);
        }
        throw new IllegalArgumentException("不支持的 Office 文件类型: " + filename);
    }

    static byte[] createXlsxFromCsv(String csv) throws Exception {
        ArrayList<ArrayList<String>> rows = parseCsv(csv);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r);
                ArrayList<String> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    row.createCell(c).setCellValue(values.get(c));
                }
            }
            int columns = rows.isEmpty() ? 0 : rows.get(0).size();
            for (int c = 0; c < Math.min(columns, 24); c++) {
                sheet.autoSizeColumn(c);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    static byte[] createDocx(String title, String markdown) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String safeTitle = blankToDefault(title, "文档");
            XWPFParagraph titleParagraph = document.createParagraph();
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(20);
            titleRun.setText(safeTitle);

            for (String line : normalizeLines(markdown)) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                String cleaned = stripMarkdownMarkers(line);
                if (line.startsWith("#")) {
                    run.setBold(true);
                    run.setFontSize(line.startsWith("##") ? 16 : 18);
                }
                run.setText(cleaned);
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    static byte[] createPptx(String title, String markdown) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String[] slides = (markdown == null ? "" : markdown).split("(?m)^---\\s*$");
            if (slides.length == 0) {
                slides = new String[]{blankToDefault(title, "演示稿")};
            }
            for (int i = 0; i < Math.min(slides.length, 40); i++) {
                addSlide(ppt, i == 0 ? blankToDefault(title, "演示稿") : "", slides[i]);
            }
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private static ExtractedOffice extractDocx(String filename, byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            StringBuilder builder = new StringBuilder();
            appendLine(builder, "文件: " + filename);
            appendLine(builder, "类型: Word 文档");
            appendLimited(builder, extractor.getText());
            return new ExtractedOffice(builder.toString(), builder.length() >= MAX_EXTRACT_CHARS);
        }
    }

    private static ExtractedOffice extractXlsx(String filename, byte[] bytes) throws Exception {
        boolean truncated = false;
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "文件: " + filename);
        appendLine(builder, "类型: Excel 工作簿");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            int sheetCount = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS);
            truncated = workbook.getNumberOfSheets() > sheetCount;
            for (int s = 0; s < sheetCount && builder.length() < MAX_EXTRACT_CHARS; s++) {
                Sheet sheet = workbook.getSheetAt(s);
                appendLine(builder, "\n工作表: " + sheet.getSheetName());
                int rowSeen = 0;
                for (Row row : sheet) {
                    if (rowSeen++ >= MAX_ROWS_PER_SHEET) {
                        truncated = true;
                        break;
                    }
                    ArrayList<String> cells = new ArrayList<>();
                    short last = row.getLastCellNum();
                    int maxCell = Math.min(last < 0 ? 0 : last, MAX_CELLS_PER_ROW);
                    for (int c = 0; c < maxCell; c++) {
                        cells.add(cellText(row.getCell(c)));
                    }
                    appendLine(builder, joinCells(cells));
                    if (builder.length() >= MAX_EXTRACT_CHARS) {
                        truncated = true;
                        break;
                    }
                }
            }
        }
        return new ExtractedOffice(limit(builder.toString()), truncated || builder.length() >= MAX_EXTRACT_CHARS);
    }

    private static ExtractedOffice extractPptx(String filename, byte[] bytes) throws Exception {
        boolean truncated = false;
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "文件: " + filename);
        appendLine(builder, "类型: PowerPoint 演示稿");
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            int slideCount = Math.min(ppt.getSlides().size(), MAX_SLIDES);
            truncated = ppt.getSlides().size() > slideCount;
            for (int i = 0; i < slideCount && builder.length() < MAX_EXTRACT_CHARS; i++) {
                XSLFSlide slide = ppt.getSlides().get(i);
                appendLine(builder, "\n第 " + (i + 1) + " 页:");
                for (XSLFTextShape shape : slide.getPlaceholders()) {
                    String text = shape.getText();
                    if (text != null && !text.trim().isEmpty()) {
                        appendLine(builder, text.trim());
                    }
                }
                slide.getShapes().forEach(shape -> {
                    if (shape instanceof XSLFTextShape && builder.length() < MAX_EXTRACT_CHARS) {
                        String text = ((XSLFTextShape) shape).getText();
                        if (text != null && !text.trim().isEmpty()) {
                            appendLine(builder, text.trim());
                        }
                    }
                });
            }
        }
        return new ExtractedOffice(limit(builder.toString()), truncated || builder.length() >= MAX_EXTRACT_CHARS);
    }

    private static void addSlide(XMLSlideShow ppt, String fallbackTitle, String markdown) {
        XSLFSlide slide = ppt.createSlide();
        String[] lines = normalizeLines(markdown).toArray(new String[0]);
        String title = fallbackTitle;
        ArrayList<String> body = new ArrayList<>();
        for (String line : lines) {
            String cleaned = stripMarkdownMarkers(line);
            if (title.isEmpty() && !cleaned.isEmpty()) {
                title = cleaned;
            } else if (!cleaned.isEmpty()) {
                body.add(cleaned);
            }
        }
        if (title.isEmpty()) {
            title = "演示稿";
        }
        XSLFTextBox titleBox = slide.createTextBox();
        XSLFTextRun titleRun = titleBox.addNewTextParagraph().addNewTextRun();
        titleRun.setBold(true);
        titleRun.setFontSize(36.0);
        titleRun.setText(title);

        XSLFTextBox bodyBox = slide.createTextBox();
        for (String line : body.isEmpty() ? bodyFromTitle(title) : body) {
            XSLFTextParagraph paragraph = bodyBox.addNewTextParagraph();
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setFontSize(23.0);
            run.setText(line);
        }
    }

    private static ArrayList<String> bodyFromTitle(String title) {
        ArrayList<String> values = new ArrayList<>();
        values.add(title);
        return values;
    }

    private static String cellText(Cell cell) {
        if (cell == null) {
            return "";
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                Date date = cell.getDateCellValue();
                return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(date);
            }
            double value = cell.getNumericCellValue();
            return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
        }
        if (type == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        if (type == CellType.ERROR) {
            return "#ERROR";
        }
        return cell.getStringCellValue();
    }

    private static ArrayList<ArrayList<String>> parseCsv(String csv) {
        ArrayList<ArrayList<String>> rows = new ArrayList<>();
        ArrayList<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        String text = csv == null ? "" : csv;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (ch == '"') {
                    quoted = false;
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                rows.add(row);
                row = new ArrayList<>();
                cell.setLength(0);
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        row.add(cell.toString());
        rows.add(row);
        return rows;
    }

    private static ArrayList<String> normalizeLines(String markdown) {
        ArrayList<String> lines = new ArrayList<>();
        String text = markdown == null ? "" : markdown;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
            if (lines.size() >= MAX_PARAGRAPHS) {
                break;
            }
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static String stripMarkdownMarkers(String value) {
        String text = value == null ? "" : value.trim();
        text = text.replaceFirst("^#{1,6}\\s*", "");
        text = text.replaceFirst("^[-*+]\\s+", "");
        text = text.replaceAll("\\*\\*", "");
        text = text.replace('`', ' ');
        return text.trim();
    }

    private static void appendLimited(StringBuilder builder, String text) {
        if (text == null || text.isEmpty() || builder.length() >= MAX_EXTRACT_CHARS) {
            return;
        }
        int remaining = MAX_EXTRACT_CHARS - builder.length();
        builder.append(text, 0, Math.min(text.length(), remaining));
    }

    private static void appendLine(StringBuilder builder, String text) {
        if (builder.length() >= MAX_EXTRACT_CHARS) {
            return;
        }
        appendLimited(builder, text == null ? "" : text);
        appendLimited(builder, "\n");
    }

    private static String joinCells(ArrayList<String> cells) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            builder.append(cells.get(i));
        }
        return builder.toString();
    }

    private static String limit(String value) {
        if (value == null || value.length() <= MAX_EXTRACT_CHARS) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_EXTRACT_CHARS);
    }

    private static String extension(String filename) {
        String value = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot) : "";
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    static class ExtractedOffice {
        final String text;
        final boolean truncated;

        ExtractedOffice(String text, boolean truncated) {
            this.text = text == null ? "" : text;
            this.truncated = truncated;
        }
    }
}
