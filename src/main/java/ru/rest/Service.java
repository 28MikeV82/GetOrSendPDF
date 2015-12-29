package ru.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.util.FileResolver;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.LocalJasperReportsContext;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    class CodeMsgException extends Exception {
        int errorCode;

        public CodeMsgException(int errorCode, String errorMessage) {
            super(errorMessage);
            this.errorCode = errorCode;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

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
    @Produces(MediaType.TEXT_PLAIN)
    public Response test() {
        try {
            JasperReportsContext jrContext = getJasperReportsContext();
            JasperReport jasperReport = getJasperReport();
            JRDataSource jrDataSource = new JREmptyDataSource();
            JasperPrint jasperPrint = JasperFillManager.getInstance(jrContext).fill(
                    jasperReport, null, jrDataSource);

            java.nio.file.Path reportPath = Paths.get(cachePathReports, "test.pdf");
            JasperExportManager.exportReportToPdfFile(jasperPrint, reportPath.toString());
        } catch (JRException e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }

        return Response.status(200).entity("done").build();
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

    @POST
    @Path("/getPDF")
    @Consumes(MEDIA_TYPE_JSON_UTF8)
    @Produces(MediaType.TEXT_PLAIN) // MediaType.APPLICATION_OCTET_STREAM_TYPE?
    public Response getPDF(String jsonRequest) {
        try {
            Map<String, String> params = parseParams(jsonRequest);
            validateParameters(params, true);
            //File report = getReport(params);
            return Response.status(200).entity(params).build();
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
            return Response.status(200).entity("{\"result\": \"not implemented yet\"").build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).build();
        }
    }

    private Map<String, String> parseParams(String request) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(request,
                new TypeReference<Map<String, String>>(){});
    }

    private void validateParameters(Map<String, String> params,
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

    private boolean paramExists(Map<String, String> map, String key) {
        return map.containsKey(key) && !"null".equals(map.get(key));
    }

    private boolean matchStrWithRegExp(Properties config, String regexpName, String str) {
        return str.matches(config.getProperty(regexpName));
    }

    private File getReport(Map<String, String> params) {
        File report = findReportInCache(params);
        if (report != null) return report;
        return generateReport(params);
    }

    private File findReportInCache(Map<String, String> params) {
        //TODO
        return null;
    }

    private File generateReport(Map<String, String> params) {
        //TODO
        return null;
    }

}
