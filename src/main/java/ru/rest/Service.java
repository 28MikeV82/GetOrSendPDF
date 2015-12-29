package ru.rest;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/vinwin")
public class Service {

    private final String CHARSET_UTF8 = ";charset=utf-8";

    @POST
    @Path("/getPDF")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN) // APPLICATION_JSON + CHARSET_UTF8)
    public Response getPDF(String request) {
        try {
            return Response.status(200).entity("request: " + request).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).build();
        }
    }

    @POST
    @Path("/sendPDF")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)//APPLICATION_JSON + CHARSET_UTF8)
    public Response sendPDF(String request) {
        try {
            return Response.status(200).entity(/*TODO*/"not implemented yet").build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(500).build();
        }
    }
}
