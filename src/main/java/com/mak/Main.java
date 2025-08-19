package com.mak;

import com.mak.service.DocTemplateService;
import com.mak.service.DocTemplateService.TemplateProcessingException;
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
        } catch (IOException | Docx4JException | TemplateProcessingException e) {
            log.error("Failed to generate PDF", e);
        }
        */

        // Sample usage of generatePdfsParallel with three templates and outputs, each having its own data map
        List<DocTemplateService.PdfGenerationRequest> requests = Arrays.asList(
                new DocTemplateService.PdfGenerationRequest("template1.docx", 
                    new HashMap<String, String>() {{ put("HEADER", "Örnek Doküman 1"); put("YAZAN", "Mehmet Ak"); put("DATE", java.time.LocalDate.now().toString()); }}, 
                    "template1.pdf"),
                new DocTemplateService.PdfGenerationRequest("template2.docx", 
                    new HashMap<String, String>() {{ put("HEADER", "Örnek Doküman 2"); put("YAZAN", "Ali Yazar"); put("DATE", java.time.LocalDate.now().toString()); }}, 
                    "template2.pdf"),
                new DocTemplateService.PdfGenerationRequest("template3.docx", 
                    new HashMap<String, String>() {{ put("HEADER", "Örnek Doküman 3"); put("YAZAN", "Veli Bozar"); put("DATE", java.time.LocalDate.now().toString()); }}, 
                    "template3.pdf")
        );
        try {
            log.info("Generating PDFs in parallel (per-template metadata): {} requests", requests.size());
            List<Path> paths = service.generatePdfsParallel(requests);
            for (Path p : paths) {
                log.info("Generated: {}", p.toAbsolutePath());
            }
        } catch (IOException | Docx4JException | TemplateProcessingException e) {
            log.error("Parallel PDF generation encountered an error", e);
        }

    }
}