package com.mak.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.docx4j.Docx4J;
import org.docx4j.XmlUtils;
import org.docx4j.fonts.BestMatchingMapper;
import org.docx4j.fonts.Mapper;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.Text;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility service for processing a DOCX template with metadata placeholders like [@KEY]
 * and exporting the result to a PDF using docx4j.
 */
public class DocTemplateService {
    private static final Logger log = LogManager.getLogger(DocTemplateService.class);
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    /**
     * Generates a PDF by loading a DOCX template from resources, replacing placeholders of the form [@KEY]
     * with values from the provided map, and exporting to the pdfs folder.
     *
     * @param templateFileName the template file name under resources (e.g., "template1.docx")
     * @param metadata         a map of keys to values, where keys correspond to placeholders without the [@ ] wrapper
     * @param outputName       desired output pdf file name (with or without .pdf extension)
     * @return the Path to the generated PDF file
     * @throws IOException    if file operations fail
     * @throws Docx4JException if docx4j processing fails
     */
    public Path generatePdf(String templateFileName, Map<String, String> metadata, String outputName) throws IOException, Docx4JException {
        if (templateFileName == null || templateFileName.trim().isEmpty()) {
            throw new IllegalArgumentException("templateFileName must not be empty");
        }
        if (outputName == null || outputName.trim().isEmpty()) {
            throw new IllegalArgumentException("outputName must not be empty");
        }

        // Normalize output file name
        String normalizedOutput = outputName.endsWith(".pdf") ? outputName : (outputName + ".pdf");
        log.info("[START] Preparing generation: template='{}', output='{}'", templateFileName, normalizedOutput);

        // Load template from resources
        try (InputStream templateStream = getResourceAsStream(templateFileName)) {
            if (templateStream == null) {
                throw new FileNotFoundException("Template not found in resources: " + templateFileName);
            }
            log.debug("Opened template stream for '{}'", templateFileName);

            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(templateStream);
            log.debug("Loaded WordprocessingMLPackage for '{}'", templateFileName);

            // Configure a robust font mapper to avoid common FOP font issues
            try {
                // Discover installed system fonts (safe no-op if already done)
                PhysicalFonts.discoverPhysicalFonts();
                Mapper fontMapper = new BestMatchingMapper();
                wordMLPackage.setFontMapper(fontMapper);
                log.debug("Font mapper configured");
            } catch (Throwable ignored) {
                // Proceed with defaults if font discovery fails
                log.debug("Font discovery/mapper configuration skipped due to an error; proceeding with defaults");
            }

            MainDocumentPart main = wordMLPackage.getMainDocumentPart();

            // Replace placeholders in the form [@KEY]
            Map<String, String> safeMeta = metadata != null ? metadata : new HashMap<>();
            log.info("Replacing placeholders for output='{}' ({} keys)", normalizedOutput, safeMeta.size());
            replacePlaceholders(main, safeMeta);
            log.debug("Placeholder replacement completed for output='{}'", normalizedOutput);

            // Ensure output directory exists
            Path pdfDir = Paths.get("pdfs");
            if (!Files.exists(pdfDir)) {
                Files.createDirectories(pdfDir);
                log.debug("Created output directory: {}", pdfDir.toAbsolutePath());
            }
            Path outPath = pdfDir.resolve(normalizedOutput);

            // Convert to PDF using docx4j (properties set via docx4j.properties on classpath)
            log.info("Converting to PDF: output='{}'", outPath.toAbsolutePath());
            try (OutputStream os = Files.newOutputStream(outPath)) {
                Docx4J.toPDF(wordMLPackage, os);
            } catch (NoClassDefFoundError ncdfe) {
                // Helpful hint when FO exporter/FOP isn't on classpath
                throw new Docx4JException("Missing classes for PDF export. Ensure dependency 'org.docx4j:docx4j-export-fo' is on the runtime classpath (use Maven exec or the shaded jar).", ncdfe);
            }

            log.info("[DONE] Generated PDF at {}", outPath.toAbsolutePath());
            return outPath;
        }
    }

    private void replacePlaceholders(MainDocumentPart main, Map<String, String> metadata) throws Docx4JException {
        try {
            // Group text nodes by their paragraph ancestor so replacements work even if placeholders are split across runs.
            List<Object> textNodes = main.getJAXBNodesViaXPath("//w:t", true);

            // Use a LinkedHashMap to preserve document order of paragraphs as encountered.
            java.util.LinkedHashMap<Object, java.util.List<Text>> paraToTexts = new java.util.LinkedHashMap<>();

            for (Object obj : textNodes) {
                Object unwrapped = XmlUtils.unwrap(obj);
                if (unwrapped instanceof Text) {
                    Text t = (Text) unwrapped;
                    Object para = getParagraphAncestor(t);
                    if (para == null) {
                        // Fallback: if we can't find paragraph ancestor, process this text node alone
                        String value = t.getValue();
                        if (value != null && !value.isEmpty()) {
                            String replaced = replaceAllPlaceholders(value, metadata);
                            if (!replaced.equals(value)) {
                                t.setValue(replaced);
                            }
                        }
                        continue;
                    }
                    paraToTexts.computeIfAbsent(para, k -> new java.util.ArrayList<>()).add(t);
                }
            }

            // Now process each paragraph's text collectively so tokens spanning runs are handled.
            for (Map.Entry<Object, java.util.List<Text>> entry : paraToTexts.entrySet()) {
                java.util.List<Text> texts = entry.getValue();
                if (texts.isEmpty()) continue;

                StringBuilder combined = new StringBuilder();
                for (Text t : texts) {
                    String v = t.getValue();
                    if (v != null) combined.append(v);
                }
                String original = combined.toString();
                if (original.isEmpty()) continue;

                String replacedAll = replaceAllPlaceholders(original, metadata);
                if (!replacedAll.equals(original)) {
                    // Write back: first text gets the entire replaced string, others are cleared
                    boolean first = true;
                    for (Text t : texts) {
                        if (first) {
                            t.setValue(replacedAll);
                            first = false;
                        } else {
                            t.setValue("");
                        }
                    }
                }
            }
        } catch (Exception ex) {
            throw new Docx4JException("Failed to replace placeholders via XPath (paragraph-aware)", ex);
        }
    }

    /**
     * Walk up the JAXB parent chain to find the enclosing paragraph (w:p) for a Text node.
     */
    private Object getParagraphAncestor(Text text) {
        Object cur = text;
        while (cur != null) {
            if (cur instanceof org.docx4j.wml.P) return cur;
            // Try to use getParent() if available
            try {
                java.lang.reflect.Method m = cur.getClass().getMethod("getParent");
                Object next = m.invoke(cur);
                cur = next;
                continue;
            } catch (Exception ignored) {
                // fall through
            }
            // Try to access a 'parent' field reflectively
            try {
                java.lang.reflect.Field f = cur.getClass().getDeclaredField("parent");
                f.setAccessible(true);
                Object next = f.get(cur);
                cur = next;
            } catch (Exception ignored) {
                cur = null;
            }
        }
        return null;
    }

    private String replaceAllPlaceholders(String text, Map<String, String> metadata) {
        String result = text;
        for (Map.Entry<String, String> e : metadata.entrySet()) {
            String key = e.getKey();
            String token = "[@" + key + "]";
            String val = e.getValue() == null ? "" : e.getValue();
            // Simple string replacement (assumes placeholders are not split across runs)
            result = result.replace(token, val);
        }
        return result;
    }

    private InputStream getResourceAsStream(String resourceName) throws IOException {
        String normalized = resourceName.startsWith("/") ? resourceName : ("/" + resourceName);
        URL url = getClass().getResource(normalized);
        if (url == null) {
            // Try without leading slash as a fallback
            url = getClass().getResource(resourceName);
        }
        if (url == null) return null;
        return url.openStream();
    }

    /**
     * Generate multiple PDFs in parallel using per-template metadata maps.
     * Each template at index i will use metadataList.get(i) for placeholder replacement.
     *
     * @param templateFileNames list of template file names under resources
     * @param metadataList      list of metadata maps, same size and order as templateFileNames
     * @param outputNames       list of output PDF names, same size and order as templateFileNames
     * @return list of Paths to the generated PDF files, in the same order as inputs
     * @throws IOException     if file operations fail or if any task fails
     * @throws Docx4JException if docx4j processing fails in any task
     */
    public List<Path> generatePdfParallel(List<String> templateFileNames, List<Map<String, String>> metadataList, List<String> outputNames)
            throws IOException, Docx4JException {
        if (templateFileNames == null || templateFileNames.isEmpty()) {
            throw new IllegalArgumentException("templateFileNames must not be empty");
        }
        if (outputNames == null || outputNames.isEmpty()) {
            throw new IllegalArgumentException("outputNames must not be empty");
        }
        if (metadataList == null || metadataList.isEmpty()) {
            throw new IllegalArgumentException("metadataList must not be empty");
        }
        if (templateFileNames.size() != outputNames.size()) {
            throw new IllegalArgumentException("templateFileNames and outputNames must have the same size");
        }
        if (templateFileNames.size() != metadataList.size()) {
            throw new IllegalArgumentException("templateFileNames and metadataList must have the same size");
        }

        int n = templateFileNames.size();
        int threads = Math.min(n, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ThreadFactory namedFactory = r -> {
            Thread t = new Thread(r);
            t.setName("pdf-worker-" + THREAD_COUNTER.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        log.info("Starting parallel PDF generation with {} thread(s) for {} task(s)", threads, n);
        ExecutorService pool = Executors.newFixedThreadPool(threads, namedFactory);

        List<Callable<Path>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String tmpl = templateFileNames.get(i);
            final String out = outputNames.get(i);
            final Map<String, String> meta = metadataList.get(i);
            tasks.add(() -> {
                String tn = Thread.currentThread().getName();
                log.info("[{}] Task #{} START: template='{}' -> output='{}'", tn, idx + 1, tmpl, out);
                try {
                    Path p = generatePdf(tmpl, meta, out);
                    log.info("[{}] Task #{} DONE: {}", tn, idx + 1, p.toAbsolutePath());
                    return p;
                } catch (Throwable t) {
                    log.error("[{}] Task #{} ERROR for template='{}' -> output='{}'", tn, idx + 1, tmpl, out, t);
                    if (t instanceof IOException) throw (IOException) t;
                    if (t instanceof Docx4JException) throw (Docx4JException) t;
                    throw new IOException("Unexpected error in task #" + (idx + 1), t);
                }
            });
        }

        List<Future<Path>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < tasks.size(); i++) {
                log.debug("Submitting task #{} to pool", i + 1);
                futures.add(pool.submit(tasks.get(i)));
            }

            List<Path> results = new ArrayList<>(n);
            List<Throwable> failures = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                Future<Path> f = futures.get(i);
                try {
                    Path res = f.get();
                    results.add(res);
                } catch (ExecutionException ee) {
                    failures.add(ee.getCause() != null ? ee.getCause() : ee);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    failures.add(ie);
                }
            }

            if (!failures.isEmpty()) {
                IOException ex = new IOException("One or more PDF generation tasks failed");
                for (Throwable t : failures) ex.addSuppressed(t);
                // Try to rethrow Docx4JException prominently if any
                for (Throwable t : failures) {
                    if (t instanceof Docx4JException) throw (Docx4JException) t;
                }
                throw ex;
            }

            log.info("All parallel PDF generation tasks completed successfully ({} items)", results.size());
            return results;
        } finally {
            pool.shutdownNow();
            log.debug("Executor service shut down");
        }
    }

}
