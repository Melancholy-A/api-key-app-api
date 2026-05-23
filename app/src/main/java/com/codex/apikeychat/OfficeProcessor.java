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
            put(zip, "ppt/presProps.xml", presPropsXml());
            put(zip, "ppt/viewProps.xml", viewPropsXml());
            put(zip, "ppt/tableStyles.xml", tableStylesXml());
            put(zip, "ppt/theme/theme1.xml", themeXml());
            put(zip, "ppt/notesMasters/notesMaster1.xml", notesMasterXml());
            put(zip, "ppt/notesMasters/_rels/notesMaster1.xml.rels", notesMasterRels());
            put(zip, "ppt/slideMasters/slideMaster1.xml", slideMasterXml());
            put(zip, "ppt/slideMasters/_rels/slideMaster1.xml.rels", slideMasterRels());
            put(zip, "ppt/slideLayouts/slideLayout1.xml", slideLayoutXml());
            put(zip, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", slideLayoutRels());
            for (int i = 0; i < slides.size(); i++) {
                put(zip, "ppt/slides/slide" + (i + 1) + ".xml", slideXml(slides.get(i), i + 1));
                put(zip, "ppt/slides/_rels/slide" + (i + 1) + ".xml.rels", slideRels(i + 1));
                put(zip, "ppt/notesSlides/notesSlide" + (i + 1) + ".xml", notesSlideXml(i + 1));
                put(zip, "ppt/notesSlides/_rels/notesSlide" + (i + 1) + ".xml.rels", notesSlideRels(i + 1));
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
                + "<Override PartName=\"/ppt/notesMasters/notesMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesMaster+xml\"/>"
                + "<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>"
                + "<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>"
                + "<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>"
                + "<Override PartName=\"/ppt/presProps.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presProps+xml\"/>"
                + "<Override PartName=\"/ppt/viewProps.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.viewProps+xml\"/>"
                + "<Override PartName=\"/ppt/tableStyles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.tableStyles+xml\"/>"
                + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
                + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>");
        for (int i = 1; i <= slideCount; i++) {
            builder.append("<Override PartName=\"/ppt/slides/slide").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>");
            builder.append("<Override PartName=\"/ppt/notesSlides/notesSlide").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.notesSlide+xml\"/>");
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
            ids.append("<p:sldId id=\"").append(255 + i).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" saveSubsetFonts=\"1\" autoCompressPictures=\"0\">"
                + "<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>"
                + "<p:sldIdLst>" + ids + "</p:sldIdLst>"
                + "<p:notesMasterIdLst><p:notesMasterId r:id=\"rId" + (slideCount + 2) + "\"/></p:notesMasterIdLst>"
                + "<p:sldSz cx=\"12192000\" cy=\"6858000\"/><p:notesSz cx=\"6858000\" cy=\"12192000\"/>"
                + defaultTextStyleXml()
                + "</p:presentation>";
    }

    private static String presentationRels(int slideCount) {
        StringBuilder builder = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>");
        for (int i = 1; i <= slideCount; i++) {
            builder.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide")
                    .append(i).append(".xml\"/>");
        }
        int next = slideCount + 2;
        builder.append("<Relationship Id=\"rId").append(next++)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster\" Target=\"notesMasters/notesMaster1.xml\"/>");
        builder.append("<Relationship Id=\"rId").append(next++)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/presProps\" Target=\"presProps.xml\"/>")
                .append("<Relationship Id=\"rId").append(next++)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/viewProps\" Target=\"viewProps.xml\"/>")
                .append("<Relationship Id=\"rId").append(next++)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>")
                .append("<Relationship Id=\"rId").append(next)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/tableStyles\" Target=\"tableStyles.xml\"/>");
        return builder.append("</Relationships>").toString();
    }

    private static String slideXml(SlideContent slide, int slideNumber) {
        StringBuilder shapes = new StringBuilder();
        shapes.append(shapeXml(2, "Text 0", slide.title, 610000, 420000, 11000000, 900000, 3600, true));
        StringBuilder body = new StringBuilder();
        for (String line : slide.lines) {
            if (body.length() > 0) {
                body.append("\n");
            }
            body.append(line);
        }
        shapes.append(shapeXml(3, "Text 1", body.toString(), 760000, 1500000, 10600000, 4700000, 2300, false));
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:cSld name=\"Slide " + slideNumber + "\"><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill></p:bgPr></p:bg><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
                + groupShapePropertiesXml()
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
            paragraphs.append("<a:p><a:pPr indent=\"0\" marL=\"0\"><a:buNone/></a:pPr><a:r><a:rPr lang=\"zh-CN\" sz=\"").append(fontSize).append("\"");
            if (bold) {
                paragraphs.append(" b=\"1\"");
            }
            paragraphs.append(" dirty=\"0\"><a:solidFill><a:srgbClr val=\"")
                    .append(bold ? "111827" : "374151")
                    .append("\"/></a:solidFill><a:latin typeface=\"Microsoft YaHei\"/><a:ea typeface=\"Microsoft YaHei\"/><a:cs typeface=\"Microsoft YaHei\"/></a:rPr><a:t>")
                    .append(xml(line.trim()))
                    .append("</a:t></a:r><a:endParaRPr lang=\"zh-CN\" sz=\"")
                    .append(fontSize)
                    .append("\"/></a:p>");
        }
        if (paragraphs.length() == 0) {
            paragraphs.append("<a:p/>");
        }
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"" + name + "\"></p:cNvPr><p:cNvSpPr/><p:nvPr></p:nvPr></p:nvSpPr>"
                + "<p:spPr><a:xfrm><a:off x=\"" + x + "\" y=\"" + y + "\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr>"
                + "<p:txBody><a:bodyPr wrap=\"square\" rtlCol=\"0\" anchor=\"t\"><a:normAutofit/></a:bodyPr><a:lstStyle/>" + paragraphs + "</p:txBody></p:sp>";
    }

    private static String slideRels(int slideNumber) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesSlide\" Target=\"../notesSlides/notesSlide" + slideNumber + ".xml\"/>"
                + "</Relationships>";
    }

    private static String slideMasterRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/>"
                + "</Relationships>";
    }

    private static String notesMasterRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/>"
                + "</Relationships>";
    }

    private static String notesSlideRels(int slideNumber) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/notesMaster\" Target=\"../notesMasters/notesMaster1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"../slides/slide" + slideNumber + ".xml\"/>"
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
                + "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
                + groupShapePropertiesXml()
                + "</p:spTree></p:cSld>"
                + "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>"
                + "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst>"
                + masterTextStylesXml()
                + "</p:sldMaster>";
    }

    private static String slideLayoutXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:sldLayout xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" type=\"blank\">"
                + "<p:cSld name=\"Blank\"><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
                + groupShapePropertiesXml()
                + "</p:spTree></p:cSld>"
                + "<p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>";
    }

    private static String notesMasterXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:notesMaster xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:cSld><p:bg><p:bgPr><a:solidFill><a:schemeClr val=\"lt1\"/></a:solidFill></p:bgPr></p:bg><p:spTree>"
                + "<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
                + groupShapePropertiesXml()
                + notesPlaceholderXml(2, "Header Placeholder 1", "hdr", 0)
                + notesPlaceholderXml(3, "Date Placeholder 2", "dt", 1)
                + notesPlaceholderXml(4, "Slide Image Placeholder 3", "sldImg", 2)
                + notesPlaceholderXml(5, "Notes Placeholder 4", "body", 3)
                + notesPlaceholderXml(6, "Footer Placeholder 5", "ftr", 4)
                + notesPlaceholderXml(7, "Slide Number Placeholder 6", "sldNum", 5)
                + "</p:spTree></p:cSld>"
                + "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>"
                + "<p:notesStyle>" + textLevelXml("lvl1pPr", 0, 1200) + textLevelXml("lvl2pPr", 457200, 1200) + textLevelXml("lvl3pPr", 914400, 1200) + "</p:notesStyle>"
                + "</p:notesMaster>";
    }

    private static String notesPlaceholderXml(int id, String name, String type, int idx) {
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"" + name + "\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"" + type + "\" idx=\"" + idx + "\"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:endParaRPr lang=\"zh-CN\"/></a:p></p:txBody></p:sp>";
    }

    private static String notesSlideXml(int slideNumber) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:notes xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>"
                + groupShapePropertiesXml()
                + "<p:sp><p:nvSpPr><p:cNvPr id=\"2\" name=\"Slide Image Placeholder 1\"/><p:cNvSpPr><a:spLocks noGrp=\"1\" noRot=\"1\" noChangeAspect=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"sldImg\"/></p:nvPr></p:nvSpPr><p:spPr/></p:sp>"
                + "<p:sp><p:nvSpPr><p:cNvPr id=\"3\" name=\"Notes Placeholder 2\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"body\" idx=\"1\"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:r><a:rPr lang=\"zh-CN\" dirty=\"0\"/><a:t></a:t></a:r><a:endParaRPr lang=\"zh-CN\" dirty=\"0\"/></a:p></p:txBody></p:sp>"
                + "<p:sp><p:nvSpPr><p:cNvPr id=\"4\" name=\"Slide Number Placeholder 3\"/><p:cNvSpPr><a:spLocks noGrp=\"1\"/></p:cNvSpPr><p:nvPr><p:ph type=\"sldNum\" sz=\"quarter\" idx=\"10\"/></p:nvPr></p:nvSpPr><p:spPr/><p:txBody><a:bodyPr/><a:lstStyle/><a:p><a:fld id=\"{F7021451-1387-4CA6-816F-3879F97B5CBC}\" type=\"slidenum\"><a:rPr lang=\"zh-CN\"/><a:t>" + slideNumber + "</a:t></a:fld><a:endParaRPr lang=\"zh-CN\"/></a:p></p:txBody></p:sp>"
                + "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:notes>";
    }

    private static String groupShapePropertiesXml() {
        return "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>";
    }

    private static String defaultTextStyleXml() {
        return "<p:defaultTextStyle>"
                + textLevelXml("lvl1pPr", 0, 1800)
                + textLevelXml("lvl2pPr", 457200, 1800)
                + textLevelXml("lvl3pPr", 914400, 1800)
                + textLevelXml("lvl4pPr", 1371600, 1800)
                + textLevelXml("lvl5pPr", 1828800, 1800)
                + textLevelXml("lvl6pPr", 2286000, 1800)
                + textLevelXml("lvl7pPr", 2743200, 1800)
                + textLevelXml("lvl8pPr", 3200400, 1800)
                + textLevelXml("lvl9pPr", 3657600, 1800)
                + "</p:defaultTextStyle>";
    }

    private static String masterTextStylesXml() {
        return "<p:txStyles>"
                + "<p:titleStyle>" + textLevelXml("lvl1pPr", 0, 3600) + "</p:titleStyle>"
                + "<p:bodyStyle>" + textLevelXml("lvl1pPr", 0, 2400) + textLevelXml("lvl2pPr", 457200, 2200) + textLevelXml("lvl3pPr", 914400, 2000) + "</p:bodyStyle>"
                + "<p:otherStyle>" + textLevelXml("lvl1pPr", 0, 1800) + "</p:otherStyle>"
                + "</p:txStyles>";
    }

    private static String textLevelXml(String tag, int marginLeft, int size) {
        return "<a:" + tag + " marL=\"" + marginLeft + "\" algn=\"l\" defTabSz=\"914400\" rtl=\"0\" eaLnBrk=\"1\" latinLnBrk=\"0\" hangingPunct=\"1\">"
                + "<a:defRPr sz=\"" + size + "\" kern=\"1200\"><a:solidFill><a:schemeClr val=\"tx1\"/></a:solidFill><a:latin typeface=\"+mn-lt\"/><a:ea typeface=\"+mn-ea\"/><a:cs typeface=\"+mn-cs\"/></a:defRPr>"
                + "</a:" + tag + ">";
    }

    private static String presPropsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:presentationPr xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:showPr><p:present/></p:showPr><p:clrMru><a:srgbClr val=\"FFFFFF\"/></p:clrMru>"
                + "</p:presentationPr>";
    }

    private static String viewPropsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<p:viewPr xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">"
                + "<p:normalViewPr><p:restoredLeft sz=\"15620\"/><p:restoredTop sz=\"94660\"/></p:normalViewPr>"
                + "<p:slideViewPr><p:cSldViewPr><p:cViewPr varScale=\"1\"><p:scale><a:sx n=\"100\" d=\"100\"/><a:sy n=\"100\" d=\"100\"/></p:scale><p:origin x=\"0\" y=\"0\"/></p:cViewPr><p:guideLst/></p:cSldViewPr></p:slideViewPr>"
                + "<p:notesTextViewPr><p:cViewPr><p:scale><a:sx n=\"100\" d=\"100\"/><a:sy n=\"100\" d=\"100\"/></p:scale><p:origin x=\"0\" y=\"0\"/></p:cViewPr></p:notesTextViewPr>"
                + "<p:gridSpacing cx=\"72008\" cy=\"72008\"/>"
                + "</p:viewPr>";
    }

    private static String tableStylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<a:tblStyleLst xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" def=\"{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}\"/>";
    }

    private static String themeXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Codex\">"
                + "<a:themeElements>"
                + "<a:clrScheme name=\"Codex\"><a:dk1><a:srgbClr val=\"111827\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"1F2937\"/></a:dk2><a:lt2><a:srgbClr val=\"F9FAFB\"/></a:lt2><a:accent1><a:srgbClr val=\"2563EB\"/></a:accent1><a:accent2><a:srgbClr val=\"10B981\"/></a:accent2><a:accent3><a:srgbClr val=\"F59E0B\"/></a:accent3><a:accent4><a:srgbClr val=\"EF4444\"/></a:accent4><a:accent5><a:srgbClr val=\"8B5CF6\"/></a:accent5><a:accent6><a:srgbClr val=\"14B8A6\"/></a:accent6><a:hlink><a:srgbClr val=\"2563EB\"/></a:hlink><a:folHlink><a:srgbClr val=\"7C3AED\"/></a:folHlink></a:clrScheme>"
                + "<a:fontScheme name=\"Codex\"><a:majorFont><a:latin typeface=\"Aptos Display\"/><a:ea typeface=\"Microsoft YaHei\"/><a:cs typeface=\"Arial\"/></a:majorFont><a:minorFont><a:latin typeface=\"Aptos\"/><a:ea typeface=\"Microsoft YaHei\"/><a:cs typeface=\"Arial\"/></a:minorFont></a:fontScheme>"
                + "<a:fmtScheme name=\"Codex\">"
                + "<a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:gradFill rotWithShape=\"1\"><a:gsLst><a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"110000\"/><a:satMod val=\"105000\"/><a:tint val=\"67000\"/></a:schemeClr></a:gs><a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"105000\"/><a:satMod val=\"103000\"/><a:tint val=\"73000\"/></a:schemeClr></a:gs></a:gsLst><a:lin ang=\"5400000\" scaled=\"0\"/></a:gradFill><a:gradFill rotWithShape=\"1\"><a:gsLst><a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:satMod val=\"103000\"/><a:lumMod val=\"102000\"/><a:tint val=\"94000\"/></a:schemeClr></a:gs><a:gs pos=\"50000\"><a:schemeClr val=\"phClr\"><a:satMod val=\"110000\"/><a:lumMod val=\"100000\"/><a:shade val=\"100000\"/></a:schemeClr></a:gs><a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"99000\"/><a:satMod val=\"120000\"/><a:shade val=\"78000\"/></a:schemeClr></a:gs></a:gsLst><a:lin ang=\"5400000\" scaled=\"0\"/></a:gradFill></a:fillStyleLst>"
                + "<a:lnStyleLst><a:ln w=\"6350\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/></a:ln><a:ln w=\"12700\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/></a:ln><a:ln w=\"19050\" cap=\"flat\" cmpd=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/></a:ln></a:lnStyleLst>"
                + "<a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst>"
                + "<a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:solidFill><a:schemeClr val=\"phClr\"><a:tint val=\"95000\"/><a:satMod val=\"170000\"/></a:schemeClr></a:solidFill><a:gradFill rotWithShape=\"1\"><a:gsLst><a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:tint val=\"93000\"/><a:satMod val=\"150000\"/><a:shade val=\"98000\"/><a:lumMod val=\"102000\"/></a:schemeClr></a:gs><a:gs pos=\"50000\"><a:schemeClr val=\"phClr\"><a:tint val=\"98000\"/><a:satMod val=\"130000\"/><a:shade val=\"90000\"/><a:lumMod val=\"103000\"/></a:schemeClr></a:gs><a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:shade val=\"63000\"/><a:satMod val=\"120000\"/></a:schemeClr></a:gs></a:gsLst><a:lin ang=\"5400000\" scaled=\"0\"/></a:gradFill></a:bgFillStyleLst>"
                + "</a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>";
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
