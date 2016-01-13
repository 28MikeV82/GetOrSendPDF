package ru.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.util.Base64;
import ru.rest.utils.CodeMsgException;
import ru.rest.utils.MacroResolver;
import ru.rest.utils.ParamsMap;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Client {

    Properties config;

    public Client(Properties config) {
        this.config = config;
    }

    public JsonNode getAvtokodHistory(ParamsMap params) throws CodeMsgException {
        ObjectNode request = buildGetDataRequest("avtokod-history", params,
                new String[]{"sts", "vin", "grz"});
        JsonNode response = performRequest(config.getProperty("getData.target"), request);
        if (response.get("errorCode").asInt() != 0)
            throw new CodeMsgException(response.get("errorCode").asInt(),
                    response.get("errorMessage").asText());
        response = response.path("result");
        return response.size() > 0 ? response : null;
    }

    public JsonNode getAvtokodOffence(ParamsMap params) throws CodeMsgException {
        ObjectNode request = buildGetDataRequest("offence-avtokod-sts", params,
                new String[]{"sts"});
        JsonNode response = performRequest(config.getProperty("getData.target"), request);
        if (response.get("errorCode").asInt() != 0)
            throw new CodeMsgException(response.get("errorCode").asInt(),
                    response.get("errorMessage").asText());
        response = response.path("result").get(0);
        return response.size() > 0 ? response : null;
    }

    public ObjectNode getAvtokodData(ParamsMap params) throws CodeMsgException {
        ObjectNode history = (ObjectNode)getAvtokodHistory(params);
        JsonNode offence = getAvtokodOffence(params);

        if (offence != null) history.put("fines", offence);

        ObjectNode commonInfo = history.with("CommonInfo");
        for (String param: new String[]{"sts", "vin", "grz"})
            commonInfo.put(param, params.get(param));

        return history;
    }

    public boolean sendEmail(ParamsMap params, String subject, String body, File report)
            throws CodeMsgException {
        ObjectNode request = buildSendEmailRequest(params, subject, body, report);
        JsonNode response = performRequest(config.getProperty("email.target"), request);
        if (response.get("errorCode").asInt() != 0)
            throw new CodeMsgException(response.get("errorCode").asInt(),
                    response.get("errorMessage").asText());
        return response.path("result").asBoolean(false);
    }

    private ObjectNode buildGetDataRequest(String dataSet, ParamsMap params, String[] termNames) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("dataSet", dataSet);
        request.put("token", params.get("token"));
        ArrayNode terms = request.putArray("terms");
        for (String termName: termNames)
            terms.addObject().put("name", termName).put("value", params.get(termName));
        return request;
    }

    private ObjectNode buildSendEmailRequest(ParamsMap params,
                                             String subject, String body, File file) {
        ObjectNode request = JsonNodeFactory.instance.objectNode();
        request.put("token", params.get("token"));
        request.put("to_email", params.get("email"));
        request.put("subject", MacroResolver.resolve(subject, params));
        request.put("body", MacroResolver.resolve(body, params));
        ArrayNode attachments = request.putArray("attachments");
        ObjectNode attachment = attachments.addObject();
        attachment.put("content_encoded", "base_64");
        attachment.put("content_type", "text/plain;charset=utf-8");
        attachment.put("filename", file.getName());
        attachment.put("content", encodeFileWithBase64(file));
        return request;
    }

    private String encodeFileWithBase64(File file) {
        try {
            Path path = Paths.get(file.toURI());
            return Base64.encodeBytes(Files.readAllBytes(path));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private ObjectNode performRequest(String target, ObjectNode request) throws CodeMsgException {
        ResteasyClient client = new ResteasyClientBuilder().build();
        Response response = client.target(target)
                .request("application/json")
                .buildPost(Entity.entity(request, "application/json"))
                .invoke();
        try {
            if (response.getStatus() != 200)
                throw new CodeMsgException(response.getStatus(), response.readEntity(String.class));
            return response.readEntity(ObjectNode.class);
        }
        finally {
            response.close();
        }
    }
}
