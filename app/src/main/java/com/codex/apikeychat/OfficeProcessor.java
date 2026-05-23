package com.codex.apikeychat;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;

class OfficeProcessor {
    static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String MIME_PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private static final int MAX_EXTRACT_CHARS = 80000;
    private static final int MAX_SHARED_STRINGS = 20000;
    private static final int MAX_SHEETS = 8;
    private static final int MAX_ROWS_PER_SHEET = 220;
    private static final int MAX_CELLS_PER_ROW = 28;
    private static final int MAX_SLIDES = 100;
    private static final int MAX_PARAGRAPHS = 360;

    private OfficeProcessor() {
    }

    static void initAndroidPoi() {
        // Kept for older callers. Office handling now uses lightweight OpenXML zip/xml code.
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

    static ExtractedOffice extract(String filename, String mimeType, File file) throws Exception {
        String ext = extension(filename);
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (".docx".equals(ext) || MIME_DOCX.equals(mime)) {
            return extractDocx(filename, file);
        }
        if (".xlsx".equals(ext) || MIME_XLSX.equals(mime)) {
            return extractXlsx(filename, file);
        }
        if (".pptx".equals(ext) || MIME_PPTX.equals(mime)) {
            return extractPptx(filename, file);
        }
        throw new IllegalArgumentException("不支持的 Office 文件类型: " + filename);
    }

    static byte[] createXlsxFromCsv(String csv) throws Exception {
        ArrayList<ArrayList<String>> rows = parseCsv(csv);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypesXlsx());
            put(zip, "_rels/.rels", rootRels("xl/workbook.xml"));
            put(zip, "xl/workbook.xml", workbookXml());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", stylesXml());
            put(zip, "xl/worksheets/sheet1.xml", sheetXml(rows));
        }
        return out.toByteArray();
    }

    static byte[] createDocx(String title, String markdown) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypesDocx());
            put(zip, "_rels/.rels", rootRels("word/document.xml"));
            put(zip, "word/document.xml", documentXml(title, markdown));
        }
        return out.toByteArray();
    }

    static byte[] createPptx(String title, String markdown) throws Exception {
        ArrayList<SlideContent> slides = slidesFromMarkdown(title, markdown);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypesPptx(slides.size()));
            put(zip, "_rels/.rels", packageRelsPptx());
            put(zip, "docProps/core.xml", coreProps());
            put(zip, "docProps/app.xml", appProps(slides.size()));
            put(zip, "ppt/presentation.xml", presentationXml(slides.size()));
            put(zip, "ppt/_rels/presentation.xml.rels", presentationRels(slides.size()));
            put(zip, "ppt/theme/theme1.xml", themeXml());
            put(zip, "ppt/slideMasters/slideMaster1.xml", slideMasterXml());
            put(zip, "ppt/slideMasters/_rels/slideMaster1.xml.rels", slideMasterRels());
            put(zip, "ppt/slideLayouts/slideLayout1.xml", slideLayoutXml());
            put(zip, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", slideLayoutRels());
            for (int i = 0; i < slides.size(); i++) {
                put(zip, "ppt/slides/slide" + (i + 1) + ".xml", slideXml(slides.get(i)));
                put(zip, "ppt/slides/_rels/slide" + (i + 1) + ".xml.rels", slideRels());
            }
        }
        return out.toByteArray();
    }

    private static ExtractedOffice extractDocx(String filename, byte[] bytes) throws Exception {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "文件: " + filename);
        appendLine(builder, "类型: Word 文档");
        boolean truncated = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    truncated = extractTextTags(zip, builder, true) || builder.length() >= MAX_EXTRACT_CHARS;
                    drainEntry(zip);
                    break;
                }
                zip.closeEntry();
            }
        }
        return new ExtractedOffice(limit(builder.toString()), truncated);
    }

    private static ExtractedOffice extractDocx(String filename, File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "文件: " + filename);
        appendLine(builder, "类型: Word 文档");
        boolean truncated = false;
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("word/document.xml");
            if (entry != null) {
                try (InputStream in = zip.getInputStream(entry)) {
                    truncated = extractTextTags(in, builder, true) || builder.length() >= MAX_EXTRACT_CHARS;
                }
            }
        }
        return new ExtractedOffice(limit(builder.toString()), truncated);
    }

    private static ExtractedOffice extractPptx(String filename, byte[] bytes) throws Exception {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "文件: " + filename);
        appendLine(builder, "类型: PowerPoint 演示稿");
        int slides = 0;
        boolean truncated = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.matches("ppt/slides/slide\\d+\\.xml")) {
                    if (slides >= MAX_SLIDES || builder.length() >= MAX_EXTRACT_CHARS) {
                        truncated = true;
                        drainEntry(zip);
                        continue;
                    }
                    slides++;
                    appendLine(builder, "\n第 " + slides + " 页:");
                    truncated = extractTextTags(zip, builder, true) || truncated;
                    drainEntry(zip);
                } else {
                    zip.closeEntry();
                }
            }
        }
        return new ExtractedOffice(limit(builder.toString()), truncated || builder.length() >= MAX_EXTRACT_CHARS);
    }

    private static ExtractedOffice extractPptx(String filename, File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "文件: " + filename);
        appendLine(builder, "类型: PowerPoint 演示稿");
        boolean truncated = false;
        try (ZipFile zip = new ZipFile(file)) {
            ArrayList<? extends ZipEntry> slideEntries = sortedEntries(zip, "ppt/slides/slide\\d+\\.xml");
            int slideCount = Math.min(slideEntries.size(), MAX_SLIDES);
            truncated = slideEntries.size() > slideCount;
            for (int i = 0; i < slideCount && builder.length() < MAX_EXTRACT_CHARS; i++) {
                ZipEntry entry = slideEntries.get(i);
                appendLine(builder, "\n第 " + (i + 1) + " 页:");
                try (InputStream in = zip.getInputStream(entry)) {
                    truncated = extractTextTags(in, builder, true) || truncated;
                }
            }
        }
        return new ExtractedOffice(limit(builder.toString()), truncated || builder.length() >= MAX_EXTRACT_CHARS);
    }

    private static ExtractedOffice extractXlsx(String filename, byte[] bytes) throws Exception {
        ArrayList<String> sharedStrings = readSharedStrings(bytes);
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "文件: " + filename);
        appendLine(builder, "类型: Excel 工作簿");
        int sheets = 0;
        boolean truncated = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.matches("xl/worksheets/sheet\\d+\\.xml")) {
                    if (sheets >= MAX_SHEETS || builder.length() >= MAX_EXTRACT_CHARS) {
                        truncated = true;
                        drainEntry(zip);
                        continue;
                    }
                    sheets++;
                    appendLine(builder, "\n工作表 " + sheets + ":");
                    truncated = extractSheet(zip, builder, sharedStrings) || truncated;
                    drainEntry(zip);
                } else {
                    zip.closeEntry();
                }
            }
        }
        return new ExtractedOffice(limit(builder.toString()), truncated || builder.length() >= MAX_EXTRACT_CHARS);
    }

    private static ExtractedOffice extractXlsx(String filename, File file) throws Exception {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "文件: " + filename);
        appendLine(builder, "类型: Excel 工作簿");
        boolean truncated = false;
        try (ZipFile zip = new ZipFile(file)) {
            ArrayList<String> sharedStrings = readSharedStrings(zip);
            ArrayList<? extends ZipEntry> sheetEntries = sortedEntries(zip, "xl/worksheets/sheet\\d+\\.xml");
            int sheetCount = Math.min(sheetEntries.size(), MAX_SHEETS);
            truncated = sheetEntries.size() > sheetCount;
            for (int i = 0; i < sheetCount && builder.length() < MAX_EXTRACT_CHARS; i++) {
                appendLine(builder, "\n工作表 " + (i + 1) + ":");
                try (InputStream in = zip.getInputStream(sheetEntries.get(i))) {
                    truncated = extractSheet(in, builder, sharedStrings) || truncated;
                }
            }
        }
        return new ExtractedOffice(limit(builder.toString()), truncated || builder.length() >= MAX_EXTRACT_CHARS);
    }

    private static ArrayList<String> readSharedStrings(byte[] bytes) throws Exception {
        ArrayList<String> values = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"xl/sharedStrings.xml".equals(entry.getName())) {
                    zip.closeEntry();
                    continue;
                }
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(zip, "UTF-8");
                StringBuilder current = null;
                boolean inText = false;
                int event;
                while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        String name = parser.getName();
                        if ("si".equals(name)) {
                            current = new StringBuilder();
                        } else if ("t".equals(name) && current != null) {
                            inText = true;
                        }
                    } else if (event == XmlPullParser.TEXT && inText && current != null) {
                        current.append(parser.getText());
                    } else if (event == XmlPullParser.END_TAG) {
                        String name = parser.getName();
                        if ("t".equals(name)) {
                            inText = false;
                        } else if ("si".equals(name) && current != null) {
                            values.add(current.toString());
                            if (values.size() >= MAX_SHARED_STRINGS) {
                                break;
                            }
                            current = null;
                        }
                    }
                }
                drainEntry(zip);
                break;
            }
        }
        return values;
    }

    private static ArrayList<String> readSharedStrings(ZipFile zip) throws Exception {
        ArrayList<String> values = new ArrayList<>();
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) {
            return values;
        }
        try (InputStream in = zip.getInputStream(entry)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, "UTF-8");
            StringBuilder current = null;
            boolean inText = false;
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("si".equals(name)) {
                        current = new StringBuilder();
                    } else if ("t".equals(name) && current != null) {
                        inText = true;
                    }
                } else if (event == XmlPullParser.TEXT && inText && current != null) {
                    current.append(parser.getText());
                } else if (event == XmlPullParser.END_TAG) {
                    String name = parser.getName();
                    if ("t".equals(name)) {
                        inText = false;
                    } else if ("si".equals(name) && current != null) {
                        values.add(current.toString());
                        if (values.size() >= MAX_SHARED_STRINGS) {
                            break;
                        }
                        current = null;
                    }
                }
            }
        }
        return values;
    }

    private static boolean extractSheet(InputStream in, StringBuilder builder, ArrayList<String> sharedStrings) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, "UTF-8");
        ArrayList<String> rowValues = null;
        String cellType = "";
        StringBuilder cellValue = null;
        boolean inCellValue = false;
        int rowCount = 0;
        boolean truncated = false;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("row".equals(name)) {
                    rowCount++;
                    if (rowCount > MAX_ROWS_PER_SHEET || builder.length() >= MAX_EXTRACT_CHARS) {
                        truncated = true;
                        break;
                    }
                    rowValues = new ArrayList<>();
                } else if ("c".equals(name) && rowValues != null) {
                    cellType = attr(parser, "t");
                    cellValue = new StringBuilder();
                } else if (("v".equals(name) || "t".equals(name)) && cellValue != null) {
                    inCellValue = true;
                }
            } else if (event == XmlPullParser.TEXT && inCellValue && cellValue != null) {
                cellValue.append(parser.getText());
            } else if (event == XmlPullParser.END_TAG) {
                String name = parser.getName();
                if ("v".equals(name) || "t".equals(name)) {
                    inCellValue = false;
                } else if ("c".equals(name) && rowValues != null && cellValue != null) {
                    if (rowValues.size() < MAX_CELLS_PER_ROW) {
                        rowValues.add(resolveCell(cellType, cellValue.toString(), sharedStrings));
                    }
                    cellValue = null;
                } else if ("row".equals(name) && rowValues != null) {
                    appendLine(builder, joinCells(rowValues));
                    rowValues = null;
                }
            }
        }
        return truncated;
    }

    private static boolean extractTextTags(InputStream in, StringBuilder builder, boolean newlineAfterText) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(in, "UTF-8");
        boolean inText = false;
        boolean truncated = false;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "t".equals(parser.getName())) {
                inText = true;
            } else if (event == XmlPullParser.TEXT && inText) {
                appendLimited(builder, parser.getText());
                if (newlineAfterText) {
                    appendLimited(builder, "\n");
                }
                if (builder.length() >= MAX_EXTRACT_CHARS) {
                    truncated = true;
                    break;
                }
            } else if (event == XmlPullParser.END_TAG && "t".equals(parser.getName())) {
                inText = false;
            }
        }
        return truncated;
    }

    private static String resolveCell(String type, String raw, ArrayList<String> sharedStrings) {
        if ("s".equals(type)) {
            try {
                int index = Integer.parseInt(raw.trim());
                if (index >= 0 && index < sharedStrings.size()) {
                    return sharedStrings.get(index);
                }
            } catch (Exception ignored) {
            }
        }
        if ("b".equals(type)) {
            return "1".equals(raw.trim()) ? "TRUE" : "FALSE";
        }
        return raw == null ? "" : raw.trim();
    }

    private static void drainEntry(InputStream in) throws Exception {
        byte[] buffer = new byte[8192];
        while (in.read(buffer) != -1) {
            // drain current zip entry
        }
    }

    private static void put(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypesDocx() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>";
    }

    private static String contentTypesXlsx() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private static String contentTypesPptx(int slideCount) {
        StringBuilder builder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>"
                + "<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>"
                + "<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>"
                + "<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>");
        for (int i = 1; i <= slideCount; i++) {
            builder.append("<Override PartName=\"/ppt/slides/slide").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>");
        }
        return builder.append("</Types>").toString();
    }

    private static String rootRels(String target) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"" + target + "\"/>"
                + "</Relationships>";
    }

    private static String packageRelsPptx() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";
    }

    private static String workbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private static String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills>"
                + "<borders count=\"1\"><border/></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
                + "</styleSheet>";
    }

    private static String sheetXml(ArrayList<ArrayList<String>> rows) {
        StringBuilder builder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            builder.append("<row r=\"").append(r + 1).append("\">");
            ArrayList<String> values = rows.get(r);
            for (int c = 0; c < values.size(); c++) {
                String ref = columnName(c) + (r + 1);
                builder.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>")
                        .append(xml(values.get(c))).append("</t></is></c>");
            }
            builder.append("</row>");
        }
        return builder.append("</sheetData></worksheet>").toString();
    }

    private static String documentXml(String title, String markdown) {
        StringBuilder builder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        paragraph(builder, blankToDefault(title, "文档"), true);
        for (String line : normalizeLines(markdown)) {
            paragraph(builder, stripMarkdownMarkers(line), line.startsWith("#"));
        }
        builder.append("<w:sectPr/></w:body></w:document>");
        return builder.toString();
    }

    private static void paragraph(StringBuilder builder, String text, boolean bold) {
        builder.append("<w:p><w:r>");
        if (bold) {
            builder.append("<w:rPr><w:b/></w:rPr>");
        }
        builder.append("<w:t xml:space=\"preserve\">").append(xml(text)).append("</w:t></w:r></w:p>");
    }

    private static String presentationXml(int slideCount) {
        StringBuilder ids = new StringBuilder();
        for (int i = 1; i <= slideCount; i++) {
            ids.append("<p:sldId id=\"").append(255 + i).append("\" r:id=\"rId").append(i).append("\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rIdMaster\"/></p:sldMasterIdLst>"
                + "<p:sldIdLst>" + ids + "</p:sldIdLst>"
                + "<p:sldSz cx=\"12192000\" cy=\"6858000\" type=\"wide\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/>"
                + "</p:presentation>";
    }

    private static String presentationRels(int slideCount) {
        StringBuilder builder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= slideCount; i++) {
            builder.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide")
                    .append(i).append(".xml\"/>");
        }
        builder.append("<Relationship Id=\"rIdMaster\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>");
        return builder.append("</Relationships>").toString();
    }

    private static String slideXml(SlideContent slide) {
        StringBuilder shapes = new StringBuilder();
        shapes.append(shapeXml(2, "Title", slide.title, 610000, 420000, 11000000, 900000, 3600, true));
        StringBuilder body = new StringBuilder();
        for (String line : slide.lines) {
            if (body.length() > 0) {
                body.append("\n");
            }
            body.append(line);
        }
        shapes.append(shapeXml(3, "Content", body.toString(), 760000, 1500000, 10600000, 4700000, 2300, false));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>"
                + shapes
                + "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>";
    }

    private static String shapeXml(int id, String name, String text, int x, int y, int cx, int cy, int fontSize, boolean bold) {
        StringBuilder paragraphs = new StringBuilder();
        String[] lines = (text == null ? "" : text).split("\\r?\\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                continue;
            }
            paragraphs.append("<a:p><a:r><a:rPr lang=\"zh-CN\" sz=\"").append(fontSize).append("\"");
            if (bold) {
                paragraphs.append(" b=\"1\"");
            }
            paragraphs.append("/><a:t>").append(xml(line.trim())).append("</a:t></a:r></a:p>");
        }
        if (paragraphs.length() == 0) {
            paragraphs.append("<a:p/>");
        }
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"" + name + "\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>"
                + "<p:spPr><a:xfrm><a:off x=\"" + x + "\" y=\"" + y + "\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>"
                + "<p:txBody><a:bodyPr wrap=\"square\"/><a:lstStyle/>" + paragraphs + "</p:txBody></p:sp>";
    }

    private static String slideRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>"
                + "</Relationships>";
    }

    private static String slideMasterRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/>"
                + "</Relationships>";
    }

    private static String slideLayoutRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/>"
                + "</Relationships>";
    }

    private static String slideMasterXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:sldMaster xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>"
                + "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>"
                + "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst>"
                + "</p:sldMaster>";
    }

    private static String slideLayoutXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:sldLayout xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" type=\"blank\">"
                + "<p:cSld name=\"Blank\"><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>"
                + "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>";
    }

    private static String themeXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Codex\">"
                + "<a:themeElements><a:clrScheme name=\"Codex\"><a:dk1><a:srgbClr val=\"111827\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"1F2937\"/></a:dk2><a:lt2><a:srgbClr val=\"F9FAFB\"/></a:lt2><a:accent1><a:srgbClr val=\"2563EB\"/></a:accent1><a:accent2><a:srgbClr val=\"10B981\"/></a:accent2><a:accent3><a:srgbClr val=\"F59E0B\"/></a:accent3><a:accent4><a:srgbClr val=\"EF4444\"/></a:accent4><a:accent5><a:srgbClr val=\"8B5CF6\"/></a:accent5><a:accent6><a:srgbClr val=\"14B8A6\"/></a:accent6><a:hlink><a:srgbClr val=\"2563EB\"/></a:hlink><a:folHlink><a:srgbClr val=\"7C3AED\"/></a:folHlink></a:clrScheme>"
                + "<a:fontScheme name=\"Codex\"><a:majorFont><a:latin typeface=\"Aptos Display\"/><a:ea typeface=\"Microsoft YaHei\"/><a:cs typeface=\"Arial\"/></a:majorFont><a:minorFont><a:latin typeface=\"Aptos\"/><a:ea typeface=\"Microsoft YaHei\"/><a:cs typeface=\"Arial\"/></a:minorFont></a:fontScheme>"
                + "<a:fmtScheme name=\"Codex\"><a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w=\"9525\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>";
    }

    private static String coreProps() {
        String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<dc:creator>Codex</dc:creator><cp:lastModifiedBy>Codex</cp:lastModifiedBy><dcterms:created xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:created><dcterms:modified xsi:type=\"dcterms:W3CDTF\">" + now + "</dcterms:modified>"
                + "</cp:coreProperties>";
    }

    private static String appProps(int slides) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\"><Application>Codex</Application><Slides>" + slides + "</Slides></Properties>";
    }

    private static ArrayList<SlideContent> slidesFromMarkdown(String title, String markdown) {
        ArrayList<SlideContent> slides = new ArrayList<>();
        String[] rawSlides = (markdown == null ? "" : markdown).split("(?m)^---\\s*$");
        for (int i = 0; i < Math.min(rawSlides.length, 40); i++) {
            ArrayList<String> lines = normalizeLines(rawSlides[i]);
            String slideTitle = i == 0 ? blankToDefault(title, "演示稿") : "";
            ArrayList<String> body = new ArrayList<>();
            for (String line : lines) {
                String cleaned = stripMarkdownMarkers(line);
                if (slideTitle.isEmpty() && !cleaned.isEmpty()) {
                    slideTitle = cleaned;
                } else if (!cleaned.isEmpty()) {
                    body.add(cleaned);
                }
            }
            if (slideTitle.isEmpty()) {
                slideTitle = "第 " + (i + 1) + " 页";
            }
            slides.add(new SlideContent(slideTitle, body));
        }
        if (slides.isEmpty()) {
            slides.add(new SlideContent(blankToDefault(title, "演示稿"), new ArrayList<>()));
        }
        return slides;
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

    private static String attr(XmlPullParser parser, String name) {
        String value = parser.getAttributeValue(null, name);
        return value == null ? "" : value;
    }

    private static ArrayList<ZipEntry> sortedEntries(ZipFile zip, String regex) {
        ArrayList<ZipEntry> entries = new ArrayList<>();
        Enumeration<? extends ZipEntry> all = zip.entries();
        while (all.hasMoreElements()) {
            ZipEntry entry = all.nextElement();
            if (!entry.isDirectory() && entry.getName().matches(regex)) {
                entries.add(entry);
            }
        }
        Collections.sort(entries, new Comparator<ZipEntry>() {
            @Override
            public int compare(ZipEntry first, ZipEntry second) {
                return Integer.compare(entryNumber(first.getName()), entryNumber(second.getName()));
            }
        });
        return entries;
    }

    private static int entryNumber(String name) {
        int end = name.lastIndexOf('.');
        int start = end - 1;
        while (start >= 0 && Character.isDigit(name.charAt(start))) {
            start--;
        }
        try {
            return Integer.parseInt(name.substring(start + 1, end));
        } catch (Exception ignored) {
            return 0;
        }
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

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index;
        do {
            name.insert(0, (char) ('A' + value % 26));
            value = value / 26 - 1;
        } while (value >= 0);
        return name.toString();
    }

    private static String xml(String value) {
        return (value == null ? "" : value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    static class ExtractedOffice {
        final String text;
        final boolean truncated;

        ExtractedOffice(String text, boolean truncated) {
            this.text = text == null ? "" : text;
            this.truncated = truncated;
        }
    }

    private static class SlideContent {
        final String title;
        final ArrayList<String> lines;

        SlideContent(String title, ArrayList<String> lines) {
            this.title = title == null ? "" : title;
            this.lines = lines == null ? new ArrayList<>() : lines;
        }
    }
}
