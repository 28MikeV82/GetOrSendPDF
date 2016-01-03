package ru.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.query.JsonQueryExecuterFactory;
import net.sf.jasperreports.engine.util.FileResolver;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.LocalJasperReportsContext;
import ru.rest.utils.CodeMsgException;
import ru.rest.utils.MacroResolver;
import ru.rest.utils.ParamsMap;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
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

        String dataPath = System.getProperty(config.getProperty("property.data.path"));
        cachePathReports = Paths.get(dataPath, config.getProperty("cache.path.reports")).toString();
        cachePathTemplates = Paths.get(cachePathReports, config.getProperty("cache.path.templates")).toString();
    }

    @GET
    @Path("/test")
    @Consumes(MEDIA_TYPE_JSON_UTF8)
    @Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
    public Response test(@QueryParam("debug")String debug,
                         @QueryParam("token")String token,
                         @QueryParam("sts")String sts,
                         @QueryParam("vin")String vin,
                         @QueryParam("grz")String grz) {
        try {
            ParamsMap params = new ParamsMap();
            params.put("token", token);
            params.put("sts", sts);
            params.put("vin", vin);
            params.put("grz", grz);
            params.put("debug", debug);

            //JsonNode reportData = getJsonReportData(params);
            /*
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(outputStream, reportData);
            ByteArrayInputStream is = new ByteArrayInputStream(outputStream.toByteArray());

            ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) != -1)
                baos.write(buffer, 0, n);
            is.close();
            String outString = new String(baos.toByteArray(), "UTF-8");
            */
            //ObjectMapper mapper = new ObjectMapper();

            File report = getReport(params);
            return Response.status(200).entity(report.getAbsolutePath()).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/getPDF")
    @Consumes(MEDIA_TYPE_JSON_UTF8)
    @Produces(MediaType.TEXT_PLAIN) // MediaType.APPLICATION_OCTET_STREAM_TYPE?
    public Response getPDF(String jsonRequest) {
        try {
            ParamsMap params = parseParams(jsonRequest);
            validateParameters(params, true);
            File report = getReport(params);
            //TODO
            return Response.status(200).entity(report.getAbsolutePath()).build();
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
            validateParameters(params, false);
            File report = getReport(params);
            Client client = new Client(config);
            Boolean result = client.sendEmail(params, report);
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
        return new ParamsMap(map);
    }

    private void validateParameters(ParamsMap params,
                                    boolean skipEmail) throws CodeMsgException {
        if (!paramExists(params, "token"))
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "Missing token");

        if (!skipEmail) {
            if (!paramExists(params, "email"))
                throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "Missing email");
            if (!matchStrWithRegExp(this.config, "regexp.check_email", params.get("email")))
                throw new CodeMsgException(ERROR_CODE_NOT_VALID_FIELD, "Invalid email");
        }

        if (!paramExists(params, "sts"))
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "Missing sts");
        if (!matchStrWithRegExp(this.config, "regexp.check_sts", params.get("sts")))
            throw new CodeMsgException(ERROR_CODE_NOT_VALID_FIELD, "Invalid sts");

        boolean vinExists = paramExists(params, "vin");
        if (vinExists)
            if (!matchStrWithRegExp(this.config, "regexp.check_vin", params.get("vin")))
                throw new CodeMsgException(ERROR_CODE_NOT_VALID_FIELD, "Invalid vin");

        boolean grzExists = paramExists(params, "grz");
        if (grzExists)
            if (!matchStrWithRegExp(this.config, "regexp.check_grz", params.get("grz")))
                throw new CodeMsgException(ERROR_CODE_NOT_VALID_FIELD, "Invalid grz");

        if (!vinExists && !grzExists)
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "vin or grz must be specified");
    }

    private boolean paramExists(ParamsMap map, String key) {
        return map.containsKey(key) && !"null".equals(map.get(key));
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
        // TODO the reports' cache should be cleaned up periodically
        if ("true".equalsIgnoreCase(config.getProperty("cache.disabled"))) return null;
        File report = new File(cachePathReports, reportName);
        return report.isFile() ? report : null;
    }

    private File generateReport(String reportName, ParamsMap params) throws Exception {
        JasperReportsContext jrContext = getJasperReportsContext();
        JasperReport jasperReport = getJasperReport();

        JsonNode reportData = getJsonReportData(params);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(outputStream, reportData);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        HashMap<String, Object> jrParams = new HashMap<>();
        jrParams.put(JsonQueryExecuterFactory.JSON_INPUT_STREAM, inputStream);
        jrParams.put(JRParameter.REPORT_LOCALE, new Locale("ru", "RU"));

        JasperPrint jasperPrint = JasperFillManager.getInstance(jrContext).fill(
                jasperReport, jrParams);

        //TODO JRDataSource jrDataSource = new JsonDataSource(getJsonReportData(params));
        //TODO JRDataSource jrDataSource = new JsonDataCollection<JsonDataSource>(???);
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
            JasperCompileManager.compileReportToFile(
                    templatePath.toString(), compiledTemplatePath.toString());
        }
        return (JasperReport)JRLoader.loadObject(compiledTemplatePath.toFile());
    }

    private JsonNode getJsonReportData(ParamsMap params) throws Exception {
        Client client = new Client(config);
        JsonNode history = client.getAvtokodHistory(params);
        JsonNode offence = client.getAvtokodOffence(params);
        client.mergeAvtokodData(history, offence);
        return history;
    }
}
