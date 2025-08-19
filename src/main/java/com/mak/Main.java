package com.mak;

import com.mak.service.DocTemplateService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.docx4j.openpackaging.exceptions.Docx4JException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;

public class Main {
    private static final Logger log = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        DocTemplateService service = new DocTemplateService();
        Map<String, String> data = new HashMap<>();
        // Sample metadata values matching placeholders like [@HEADER] in the template
        data.put("HEADER", "Örnek Doküman");
        data.put("YAZAN", "Mehmet Ak");
        data.put("DATE", java.time.LocalDate.now().toString());

/*
        String template = "template1.docx"; // located under src/main/resources
        String outputName = "template1.pdf"; // will be saved as pdfs/example-output.pdf
        try {
            log.info("Generating PDF from template '{}' to output name '{}'", template, outputName);
            Path pdfPath = service.generatePdf(template, data, outputName);
            log.info("PDF generated at: {}", pdfPath.toAbsolutePath());
        } catch (IOException | Docx4JException e) {
            log.error("Failed to generate PDF", e);
        }
*/

        // Sample usage of generatePdfParallel with three templates and outputs, each having its own data map
        List<String> templates = Arrays.asList("template1.docx", "template2.docx", "template3.docx");
        List<String> outputs = Arrays.asList("template1.pdf", "template2.pdf", "template3.pdf");
        List<Map<String, String>> dataList = Arrays.asList(
                new HashMap<String, String>() {{ put("HEADER", "Örnek Doküman 1"); put("YAZAN", "Mehmet Ak"); put("DATE", java.time.LocalDate.now().toString()); }},
                new HashMap<String, String>() {{ put("HEADER", "Örnek Doküman 2"); put("YAZAN", "Ali Yazar"); put("DATE", java.time.LocalDate.now().toString()); }},
                new HashMap<String, String>() {{ put("HEADER", "Örnek Doküman 3"); put("YAZAN", "Veli Bozar"); put("DATE", java.time.LocalDate.now().toString()); }}
        );
        try {
            log.info("Generating PDFs in parallel (per-template metadata): {} -> {}", templates, outputs);
            List<Path> paths = service.generatePdfParallel(templates, dataList, outputs);
            for (Path p : paths) {
                log.info("Generated: {}", p.toAbsolutePath());
            }
        } catch (IOException | Docx4JException e) {
            log.error("Parallel PDF generation encountered an error", e);
        }

    }
}