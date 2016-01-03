package ru.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import ru.rest.utils.CodeMsgException;
import ru.rest.utils.ParamsMap;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Properties;

public class Client {

    public JsonNode getAvtokodHistory(ParamsMap params, Properties config) throws CodeMsgException {
        ObjectNode request = buildGetDataRequest("avtokod-history", params,
                new String[]{"sts", "vin", "grz"});
        JsonNode response = performRequest(config.getProperty("getData.target"), request);
        if (response.get("errorCode").asInt() != 0)
            throw new CodeMsgException(response.get("errorCode").asInt(),
                    response.get("errorMessage").asText());
        response = response.path("result");
        return response.size() > 0 ? response : null;
    }

    public JsonNode getAvtokodOffence(ParamsMap params, Properties config) throws CodeMsgException {
        ObjectNode request = buildGetDataRequest("offence-avtokod-sts", params,
                new String[]{"sts"});
        JsonNode response = performRequest(config.getProperty("getData.target"), request);
        if (response.get("errorCode").asInt() != 0)
            throw new CodeMsgException(response.get("errorCode").asInt(),
                    response.get("errorMessage").asText());
        response = response.path("result").get(0);
        return response.size() > 0 ? response : null;
    }

    public void mergeAvtokodData(JsonNode history, JsonNode offence) {
        if (offence != null)
            ((ObjectNode)history).put("fines", offence);
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

    private JsonNode performRequest(String target, ObjectNode request) throws CodeMsgException {
        ResteasyClient client = new ResteasyClientBuilder().build();
        Response response = client.target(target)
                .request("application/json")
                .buildPost(Entity.entity(request, "application/json"))
                .invoke();
        try {
            if (response.getStatus() != 200)
                throw new CodeMsgException(response.getStatus(), response.readEntity(String.class));
            JsonNode result = response.readEntity(JsonNode.class);
            return result;
        }
        finally {
            response.close();
        }
    }
}
