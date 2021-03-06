package ru.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.util.FileResolver;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.engine.util.LocalJasperReportsContext;
import ru.rest.utils.CodeMsgException;
import ru.rest.utils.MacroResolver;
import ru.rest.utils.ParamsMap;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

@Path("/vinwin")
public class Service {

    final int ERROR_CODE_MISSING_FIELDS = 405;
    final int ERROR_CODE_NOT_VALID_FIELD = 406;
    final String MEDIA_TYPE_JSON_UTF8 = MediaType.APPLICATION_JSON + ";charset=utf-8";

    final Properties config = new Properties();
    String cachePathReports;
    String cachePathTemplates;

    public Service() {
        try {
            InputStreamReader inr = null;
            InputStream in = getClass().getResourceAsStream("/config.properties");
            try {
                inr = new InputStreamReader(in, "UTF-8");
                config.load(inr);
            } finally {
                if (inr != null) inr.close();
                in.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String dataPath = System.getProperty(config.getProperty("jboss.prop.data.path"));
        cachePathReports = Paths.get(dataPath, config.getProperty("cache.path.reports")).toString();
        cachePathTemplates = Paths.get(dataPath, config.getProperty("report.template.path")).toString();
    }

    @POST
    @Path("/getPDF")
    @Consumes(MEDIA_TYPE_JSON_UTF8)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getPDF(String jsonRequest) {
        try {
            ParamsMap params = parseParams(jsonRequest);
            validateToken(params);
            validateParameters(params);
            final File report = getReport(params);
            return Response
                    .ok()
                    .header("content-disposition", "attachment;" +
                            "filename=\"" + URLEncoder.encode(report.getName(), "utf-8") + "\"")
                    .entity(new StreamingOutput() {
                        @Override
                        public void write(OutputStream outputStream)
                                throws IOException, WebApplicationException {
                            Files.copy(report.toPath(), outputStream);
                        }
                    })
                    .build();
        } catch(CodeMsgException e) {
            e.printStackTrace();
            return Response.status(e.getErrorCode()).entity(e.getMessage()).build();
        } catch(Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/sendPDF")
    @Consumes(MEDIA_TYPE_JSON_UTF8)
    @Produces(MEDIA_TYPE_JSON_UTF8)
    public Response sendPDF(String request) {
        try {
            ParamsMap params = parseParams(request);
            validateToken(params);
            validateEmail(params);
            validateParameters(params);

            File report = getReport(params);
            String subject = config.getProperty("email.sendPdf.subject");
            String body = loadFileResourceAsString("email.sendPdf.body.template");

            Client client = new Client(config);
            Boolean result = client.sendEmail(params, subject, body, report);

            return Response.status(200).entity(buildResponse(0, "", result)).build();
        } catch (CodeMsgException e) {
            return Response.status(200).entity(
                    buildResponse(e.getErrorCode(), e.getMessage(), false)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).build();
        }
    }

    @POST
    @Path("/exampleSend")
    @Consumes(MEDIA_TYPE_JSON_UTF8)
    @Produces(MEDIA_TYPE_JSON_UTF8)
    public Response exampleSend(String request) {
        try {
            ParamsMap params = parseParams(request);
            validateToken(params);
            validateEmail(params);

            File report = new File(cachePathTemplates,
                    config.getProperty("email.exampleSend.report.template"));
            String subject = config.getProperty("email.exampleSend.subject");
            String body = loadFileResourceAsString("email.exampleSend.body.template");

            Client client = new Client(config);
            Boolean result = client.sendEmail(params, subject, body, report);

            return Response.status(200).entity(buildResponse(0, "", result)).build();
        } catch (CodeMsgException e) {
            return Response.status(200).entity(
                    buildResponse(e.getErrorCode(), e.getMessage(), false)).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).build();
        }
    }

    private ObjectNode buildResponse(int errorCode, String errorMessage, Object result) {
        ObjectNode response = JsonNodeFactory.instance.objectNode();
        response.put("errorCode", errorCode);
        response.put("errorMessage", errorMessage);
        response.put("result", result.toString());
        return response;
    }

    private ParamsMap parseParams(String request) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        HashMap<String, String> map = mapper.readValue(request,
                new TypeReference<HashMap<String, String>>(){});

        ParamsMap params = new ParamsMap();
        for(Map.Entry<String, String> e: map.entrySet())
            if (!"null".equalsIgnoreCase(e.getValue()))
                params.put(e.getKey(), e.getValue());
        return params;
    }

    private void validateToken(ParamsMap params) throws CodeMsgException {
        if (!params.containsKey("token"))
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "Missing token");
    }

    private void validateEmail(ParamsMap params) throws CodeMsgException {
        if (!params.containsKey("email"))
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "Missing email");
        if (!matchStrWithRegExp(this.config, "regexp.check_email", params.get("email")))
            throw new CodeMsgException(ERROR_CODE_NOT_VALID_FIELD, "Invalid email");
    }

    private void validateParameters(ParamsMap params) throws CodeMsgException {
        if (!params.containsKey("sts"))
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "Missing sts");
        if (!matchStrWithRegExp(this.config, "regexp.check_sts", params.get("sts")))
            throw new CodeMsgException(ERROR_CODE_NOT_VALID_FIELD, "Invalid sts");

        boolean vinExists = params.containsKey("vin");
        if (vinExists)
            if (!matchStrWithRegExp(this.config, "regexp.check_vin", params.get("vin")))
                throw new CodeMsgException(ERROR_CODE_NOT_VALID_FIELD, "Invalid vin");

        boolean grzExists = params.containsKey("grz");
        if (grzExists)
            if (!matchStrWithRegExp(this.config, "regexp.check_grz", params.get("grz")))
                throw new CodeMsgException(ERROR_CODE_NOT_VALID_FIELD, "Invalid grz");

        if (!vinExists && !grzExists)
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "vin or grz must be specified");
    }

    private boolean matchStrWithRegExp(Properties config, String regexpName, String str) {
        return str.matches(config.getProperty(regexpName));
    }

    private File getReport(ParamsMap params) throws Exception {
        String reportName = buildReportName(params);
        File report = findReportInCache(reportName);
        if (report != null) return report;
        return generateReport(reportName, params);
    }

    private String buildReportName(ParamsMap params) {
        return MacroResolver.resolve(config.getProperty("report.mask"), params);
    }

    private File findReportInCache(String reportName) {
        // TODO
        // Probably we need to store files not in a folder using flat list
        //   but in folders' hierarchy because if there are huge number of files
        //   the search will work too long
        if ("true".equalsIgnoreCase(config.getProperty("cache.disabled"))) return null;
        File report = new File(cachePathReports, reportName);
        return report.isFile() ? report : null;
    }

    private File generateReport(String reportName, ParamsMap params) throws Exception {
        JasperReportsContext jrContext = getJasperReportsContext();
        JasperReport jasperReport = getJasperReport();

        JsonNode reportData = getJsonReportData(params);

        HashMap<String, Object> jrParams = new HashMap<>();
        jrParams.put(JRParameter.REPORT_LOCALE, new Locale("ru", "RU"));
        jrParams.put("JSON_INPUT_STREAM",
                convertJsonNodeToInputStream(reportData));
        jrParams.put("SUB_DATA_SOURCE_OWNERS",
                getJsonSubDataSource(reportData.path("VladHist").path("VladHistTable")));
        jrParams.put("SUB_DATA_SOURCE_TECH_INSPECTIONS",
                getJsonSubDataSource(reportData.path("GTO_Part").path("GTO")));
        jrParams.put("SUB_DATA_SOURCE_INSURANCE_CASES",
                getJsonSubDataSource(reportData.path("Insurance_Part").path("Insurance")));
        jrParams.put("SUB_DATA_SOURCE_ACCIDENTS",
                getJsonSubDataSource(reportData.path("DTP_Part").path("DTP")));
        jrParams.put("SUB_DATA_SOURCE_COMMERCIAL_USES",
                getJsonSubDataSource(reportData.path("Kommercial_Part").path("Kommercial")));
        jrParams.put("SUB_DATA_SOURCE_FINES",
                getJsonSubDataSource(reportData.path("fines")));

        JasperPrint jasperPrint = JasperFillManager.getInstance(jrContext).fill(
                jasperReport, jrParams);
        java.nio.file.Path reportPath = Paths.get(cachePathReports, reportName);
        JasperExportManager.getInstance(jrContext).exportReportToPdfFile(
                jasperPrint, reportPath.toString());
        return reportPath.toFile();
    }

    private LocalJasperReportsContext getJasperReportsContext() {
        LocalJasperReportsContext jrContext = new LocalJasperReportsContext(
                DefaultJasperReportsContext.getInstance());
        jrContext.setClassLoader(getClass().getClassLoader());
        jrContext.setFileResolver(new FileResolver() {
            @Override
            public File resolveFile(String fileName){
                return new File(cachePathTemplates, fileName);
            }
        });
        return jrContext;
    }

    private JasperReport getJasperReport() throws JRException {
        String compiledTemplateName = config.getProperty("report.template.jasper");
        java.nio.file.Path compiledTemplatePath = Paths.get(cachePathTemplates, compiledTemplateName);
        if (!Files.exists(compiledTemplatePath)) {
            String templateName = config.getProperty("report.template.jrxml");
            java.nio.file.Path templatePath = Paths.get(cachePathTemplates, templateName);
            JasperReport report = JasperCompileManager.compileReport(templatePath.toString());
            updateStylesWithPdfFont(report);
            JRSaver.saveObject(report, compiledTemplatePath.toString());
            makeReportsCacheDir();
            return report;
        }
        return (JasperReport)JRLoader.loadObject(compiledTemplatePath.toFile());
    }

    private void updateStylesWithPdfFont(JasperReport report) {
        String pdfFontFile = config.getProperty("report.template.pdf_font");
        String pdfFontPath = new File(cachePathTemplates, pdfFontFile).getAbsolutePath();
        for(JRStyle style: report.getStyles())
            style.setPdfFontName(pdfFontPath);
    }

    private void makeReportsCacheDir() {
        File reportCache = new File(cachePathReports);
        if (!reportCache.exists()) reportCache.mkdirs();
    }

    private JsonNode getJsonReportData(ParamsMap params) throws Exception {
        Client client = new Client(config);
        return client.getAvtokodData(params);
    }

    private JsonDataSource getJsonSubDataSource(JsonNode node) throws Exception {
        return new JsonDataSource(convertJsonNodeToInputStream(node));
    }

    private InputStream convertJsonNodeToInputStream(JsonNode node) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(outputStream, node);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    private String loadFileResourceAsString(String resourceName) throws IOException {
        String bodyTemplate = config.getProperty(resourceName);
        InputStream inputStream = getClass().getResourceAsStream(bodyTemplate);
        InputStreamReader streamReader = new InputStreamReader(inputStream, "UTF-8");
        StringBuilder sb = new StringBuilder(1024);
        char[] buffer = new char[1024];
        for (int n; (n = streamReader.read(buffer)) != -1; sb.append(buffer, 0, n));
        return sb.toString();
    }
}
