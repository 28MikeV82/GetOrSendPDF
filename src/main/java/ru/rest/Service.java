package ru.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.Map;

@Path("/vinwin")
public class Service {

    final int ERROR_CODE_MISSING_FIELDS = 405;
    final int ERROR_CODE_NOT_VALID_FIELD = 406;

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

    @POST
    @Path("/getPDF")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN) // MediaType.APPLICATION_OCTET_STREAM_TYPE?
    public Response getPDF(String jsonRequest) {
        try {
            Map<String, String> params = parseParams(jsonRequest);
            validateParameters(params, true);
            //File report = getReport(params);
            return Response.status(200).entity(params).build();
        } catch(CodeMsgException e) {
            return Response.status(e.getErrorCode()).entity(e.getMessage()).build();
        } catch(Exception e) {
            e.printStackTrace();
            return Response.status(500).entity(e.getMessage()).build();
        }
    }

    @POST
    @Path("/sendPDF")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
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
        if (!params.containsKey("token"))
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "Missing token");
        if (!skipEmail && !params.containsKey("email"))
            throw new CodeMsgException(ERROR_CODE_MISSING_FIELDS, "Missing email");

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
