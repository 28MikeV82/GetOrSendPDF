package ru.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.query.JsonQueryExecuterFactory;
import net.sf.jasperreports.engine.util.FileResolver;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.LocalJasperReportsContext;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    @Path("/test/{token}/{sts}/{vin}/{grz}/{debug}")
    @Consumes(MEDIA_TYPE_JSON_UTF8)
    @Produces(MediaType.TEXT_PLAIN + ";charset=utf-8")
    public Response test(@PathParam("token")String token,
                         @PathParam("sts")String sts,
                         @PathParam("vin")String vin,
                         @PathParam("grz")String grz,
                         @PathParam("debug")String debug) {
        try {
            HashMap<String, String> params = new HashMap<>();
            params.put("token", token);
            params.put("sts", sts);
            params.put("vin", vin);
            params.put("grz", grz);
            params.put("debug", debug);

            /*
            InputStream is = getJsonReportData(params);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(8192);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) != -1)
                baos.write(buffer, 0, n);
            is.close();
            */

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

    private File getReport(Map<String, String> params) throws Exception {
        String reportName = buildReportName(params);
        File report = findReportInCache(reportName);
        if (report != null) return report;
        return generateReport(reportName, params);
    }

    /**
     * The method generates report name using a mask which is specified by 'report.mask' property
     *   from config.properties. Mask contain few macros with syntax like %param1|param2|'default'%
     *   and each param is resolved by name within passed <b>params</b>.
     *   For example "%grz|vin|'unknown'%.pdf" mask is resolved to "grz-value.pdf" if grz is
     *   specified in passed <b>params</b> or "vin-value.pdf" if grz is not specified but
     *   vin is specified or "unknown.pdf" if grz and vin are not specified.
     * @param params
     * @return report name with resolved macros
     */
    private String buildReportName(Map<String, String> params) {
        String mask = config.getProperty("report.mask");
        String name = mask;
        Matcher matcher = Pattern.compile("(%.+?%)").matcher(mask);
        while (matcher.find()) {
            String macro = matcher.group();
            String trimmedMacro = macro.substring(1, macro.length() - 1);
            String replacement = "";
            for (String param: trimmedMacro.split("\\|")) {
                if (paramExists(params, param)) {
                    replacement = params.get(param);
                    break;
                }
                if (param.startsWith("'")) {
                    replacement = param.substring(1, param.length() - 1);
                    break;
                }
            }
            name = name.replace(macro, replacement);
        }
        return name;
    }

    private File findReportInCache(String reportName) {
        if ("true".equalsIgnoreCase(config.getProperty("cache.disabled"))) return null;
        File report = new File(cachePathReports, reportName);
        return report.isFile() ? report : null;
    }

    private File generateReport(String reportName, Map<String, String> params) throws Exception {
        JasperReportsContext jrContext = getJasperReportsContext();
        JasperReport jasperReport = getJasperReport();

        Map<String, Object> jrParams = new HashMap<>();
        jrParams.put(JsonQueryExecuterFactory.JSON_INPUT_STREAM, getJsonReportData(params));
        jrParams.put(JRParameter.REPORT_LOCALE, new Locale("ru", "RU"));

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
            JasperCompileManager.compileReportToFile(
                    templatePath.toString(), compiledTemplatePath.toString());
        }
        return (JasperReport)JRLoader.loadObject(compiledTemplatePath.toFile());
    }

    private InputStream getJsonReportData(Map<String, String> params) throws Exception {
        //TODO
        String jsonStr =
                "{\n" +
                "        \"common\":\n" +
                "        {\n" +
                "        \t\"sts\":\"77РМ214031\",\n" +
                "        \t\"grz\":\"В203РХ177\",\n" +
                "        \t\"vin\":\"XWEHN512BD0001573\",\n" +
                "        \t\"model\": \"JEEP\",\n" +
                "            \"year\": 2011,\n" +
                "            \"back\": \"Внедорожник\",\n" +
                "            \"ecologyclass\": \"4-й класс, салярка\",\n" +
                "            \"motorhourcepower\": \"149\",\n" +
                "            \"motorpower\": \"42\",\n" +
                "            \"ownerscount\": \"2\",\n" +
                "            \"color\": \"Коричневый\",\n" +
                "            \"customcountry\": \"США\"\n" +
                "        },\n" +
                "        \"car\":\n" +
                "        {\n" +
                "            \"model\": \"JEEP\",\n" +
                "            \"year\": 2011,\n" +
                "            \"back\": \"Внедорожник\",\n" +
                "            \"ecologyclass\": \"4-й класс, салярка\",\n" +
                "            \"motorhourcepower\": \"149\",\n" +
                "            \"motorpower\": \"42\",\n" +
                "            \"ownerscount\": \"2\",\n" +
                "            \"color\": \"Коричневый\",\n" +
                "            \"customcountry\": \"США\",\n" +
                "            \"restrictions\":\n" +
                "            [\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"code\": \"Запрет на снятие с учета\",\n" +
                "    \"name\": \"Запрет на снятие с учета\"\n" +
                "},\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"code\": \"Запрет на регистрационные действия и прохождение ТО\",\n" +
                "    \"name\": \"Запрет на регистрационные действия и прохождение ТО\"\n" +
                "},\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"code\": \"Утилизация (для транспорта не старше 5 лет)\",\n" +
                "    \"name\": \"Утилизация (для транспорта не старше 5 лет)\"\n" +
                "},\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"code\": \"Аннулирование регистрации\",\n" +
                "    \"name\": \"Аннулирование регистрации\"\n" +
                "},\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"code\": \"Нахождение в розыске\",\n" +
                "    \"name\": \"Нахождение в розыске\"\n" +
                "}\n" +
                "            ],\n" +
                "            \"owners\": \n" +
                "            [\n" +
                "                {\n" +
                "                    \"number\": \"Василий Петров\",\n" +
                "                    \"period\": \"май 2005 - сен 2009\",\n" +
                "                    \"type\": \"Физическое лицо\" \n" +
                "                },\n" +
                "                {\n" +
                "                    \"number\": \"2\",\n" +
                "                    \"period\": \"окт 2009 - дек 2012\",\n" +
                "                    \"type\": \"Юридическое лицо лицо\" \n" +
                "                }\n" +
                "            ],\n" +
                "            \"accidents\": \n" +
                "            [\n" +
                "                {\n" +
                "                    \"type\": \"Наезд на стоящее ТС\",\n" +
                "                    \"date\": \"21.09.2015\",\n" +
                "                    \"place\": \"Московская область\",\n" +
                "                    \"damage\": \"зад справа, зад слева\" \n" +
                "                },\n" +
                "                {\n" +
                "                    \"type\": \"столкновение\",\n" +
                "                    \"date\": \"10.05.2015\",\n" +
                "                    \"place\": \"Калининград\",\n" +
                "                    \"damage\": \"днище снизу\" \n" +
                "                }\n" +
                "            ],\n" +
                "            \"insurancecases\": \n" +
                "            [\n" +
                "                {\n" +
                "                    \"accident\": \"Поцарапл бамбер на парковке\",\n" +
                "                    \"damage\": \"зад справа, зад слева\",\n" +
                "                    \"date\": \"21.09.2015\",\n" +
                "                    \"type\": \"выплачено по ОСАГО\" \n" +
                "                },\n" +
                "                {\n" +
                "                    \"accident\": \"ДТП на перекрестке\",\n" +
                "                    \"damage\": \"днище снизу\",\n" +
                "                    \"date\": \"10.05.2015\",\n" +
                "                    \"type\": \"выплачено по ОСАГО\" \n" +
                "                },\n" +
                "{\n" +
                "    \"damage\": \"Задняя дверь правая; Крыло заднее правое\",\n" +
                "    \"type\": \"КАСКО\",\n" +
                "    \"date \": \"21.09.2012\",\n" +
                "    \"accident\": \"Страховой случай\"\n" +
                "}\n" +
                "            ],\n" +
                "            \"technicalinspections\":[\n" +
                "{\n" +
                "    \"result\": \"ТС исправно\",\n" +
                "    \"date\": \"03.07.2015\",\n" +
                "    \"place\": \"г. Москва/общество с ограниченной ответственностью \\\"АКВАТЕХСЕРВИС\\\"\"\n" +
                "}                     \n" +
                "            ],\n" +
                "            \"commercialuses\": \n" +
                "            [\n" +
                "                {\n" +
                "                    \"name\": \"Использование в качестве такси\",\n" +
                "                    \"result\": \"нет\" \n" +
                "                },\n" +
                "                {\n" +
                "                    \"name\": \"Использование в качестве грузового транспорта\",\n" +
                "                    \"result\": \"нет\" \n" +
                "                },\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"name\": \"Использование в качестве такси\"\n" +
                "},\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"name\": \"Использование в качестве маршрутного транспорта\"\n" +
                "},\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"name\": \"Использование в качестве грузового транспорта\"\n" +
                "},\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"name\": \"Использование в качестве специального транспорта (городские службы, аварийные службы и прочее)\"\n" +
                "},\n" +
                "{\n" +
                "    \"result\": \"нет\",\n" +
                "    \"name\": \"Прочие виды\"\n" +
                "}\n" +
                "            ]\n" +
                "        }, \n" +
                "        \"fines\": \n" +
                "        [\n" +
                "            {\n" +
                "                \"payed\": true,\n" +
                "                \"amount\": 4000,\n" +
                "                \"date\": \"21.09.2015\",\n" +
                "                \"number\": \"18883742763672364723\",\n" +
                "                \"name\": \"Постановление по видеофиксации №18883742763672364723\",\n" +
                "                \"offend_name\": \"превышение допустимой скорости\",\n" +
                "                \"offend_time\": \"18.09.2015 10:32\",\n" +
                "                \"offend_place\": \"Москва, Ул. Тимура Фрунзе, вдоль д17к30\",\n" +
                "                \"reportPlace\": \"Скаковая д. 19, Беговой р-н\",\n" +
                "                \"authority\": \"ГКУ \\\"АМП\\\"\",\n" +
                "                \"offender\": \"Владимир Константинович\",\n" +
                "                \"TS\": \"К 060 УА 777\",\n" +
                "                \"images\": [\n" +
                "                    \"/image/73fb92ae-8a5b-4474-abcf-ddb6c620bf19\",\n" +
                "                    \"/image/73fb92ae-8a5b-4474-abcf-ddb6c620bf19\" \n" +
                "                ]\n" +
                "            },\n" +
                "            {\n" +
                "                \"payed\": false,\n" +
                "                \"amount\": 1000,\n" +
                "                \"date\": \"01.05.2015\",\n" +
                "                \"number\": \"1888374389489348593485\",\n" +
                "                \"name\": \"Постановление №1888374389489348593485 от 01.05.2015\",\n" +
                "                \"offend_name\": \"не уступил пешеходу\",\n" +
                "                \"offend_time\": \"01.05.2015 12:44\",\n" +
                "                \"offend_place\": \"Москва, Ул. Тимура Фрунзе, вдоль д17к30\",\n" +
                "                \"reportPlace\": \"Скаковая д. 19, Беговой р-н\",\n" +
                "                \"authority\": \"офицер полиции\",\n" +
                "                \"offender\": \"Владимир Константинович\",\n" +
                "                \"TS\": \"К 060 УА 777\",\n" +
                "                \"images\": null\n" +
                "            }\n" +
                "        ]\n" +
                "}";
        return new ByteArrayInputStream(jsonStr.getBytes("UTF-8"));
    }
}
