package com.mak.service;

import com.mak.service.DocTemplateService.TemplateProcessingException;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DocTemplateServiceTest {

    private Path generated;

    @After
    public void cleanup() throws IOException {
        if (generated != null && Files.exists(generated)) {
            Files.delete(generated);
        }
    }

    @Test
    public void generatesPdfFromTemplate() throws IOException, Docx4JException, TemplateProcessingException {
        DocTemplateService service = new DocTemplateService();
        Map<String, String> data = new HashMap<>();
        data.put("HEADER", "Header From Test");
        data.put("NAME", "Jane Tester");
        data.put("DATE", "2025-08-19");

        generated = service.generatePdf("template1.docx", data, "junit-output");

        Assert.assertNotNull(generated);
        Assert.assertTrue("PDF should be created", Files.exists(generated));
        Assert.assertTrue("output should be under pdfs folder", generated.toString().contains("pdfs"));
        Assert.assertTrue("filename should end with .pdf", generated.getFileName().toString().endsWith(".pdf"));
    }
}
