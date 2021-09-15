package de.samply.share.broker.rest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mchange.util.AlreadyExistsException;
import de.samply.share.broker.control.SearchController;
import de.samply.share.broker.control.SiteController;
import de.samply.share.broker.filter.AuthenticatedUser;
import de.samply.share.broker.filter.Secured;
import de.samply.share.broker.model.db.tables.pojos.BankSite;
import de.samply.share.broker.model.db.tables.pojos.Document;
import de.samply.share.broker.model.db.tables.pojos.Inquiry;
import de.samply.share.broker.model.db.tables.pojos.InquirySite;
import de.samply.share.broker.model.db.tables.pojos.Reply;
import de.samply.share.broker.model.db.tables.pojos.Site;
import de.samply.share.broker.model.db.tables.pojos.User;
import de.samply.share.broker.monitoring.IcingaController;
import de.samply.share.broker.statistics.NTokenHandler;
import de.samply.share.broker.utils.Utils;
import de.samply.share.broker.utils.db.BankSiteUtil;
import de.samply.share.broker.utils.db.BankUtil;
import de.samply.share.broker.utils.db.DocumentUtil;
import de.samply.share.broker.utils.db.InquirySiteUtil;
import de.samply.share.broker.utils.db.InquiryUtil;
import de.samply.share.broker.utils.db.SiteUtil;
import de.samply.share.common.model.dto.SiteInfo;
import de.samply.share.common.utils.Constants;
import de.samply.share.common.utils.ProjectInfo;
import de.samply.share.common.utils.SamplyShareUtils;
import de.samply.share.essentialquery.EssentialSimpleQueryDto;
import java.sql.Timestamp;
import java.util.List;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBException;
import org.apache.commons.lang.StringUtils;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jooq.tools.json.JSONArray;
import org.jooq.tools.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class Searchbroker provides the REST resources for samply.share.client (and other similar
 * products) to use the decentral search.
 */
@Path("/searchbroker")
public class Searchbroker {

  private static final String CONFIG_PROPERTY_BROKER_NAME = "broker.name";
  private static final String CONTENT_TYPE_PDF = "application/pdf";

  private static final Logger LOGGER = LoggerFactory.getLogger(Searchbroker.class);

  private final String serverHeaderValue =
      Constants.SERVER_HEADER_VALUE_PREFIX + ProjectInfo.INSTANCE.getVersionString();

  private final BankRegistration bankRegistration = new BankRegistration();

  private final InquiryHandler inquiryHandler = new InquiryHandler();

  private Gson gson = new Gson();

  @Context
  private UriInfo uriInfo;

  @Inject
  @AuthenticatedUser
  private User authenticatedUser;

  @Inject
  private IcingaController icingaController;

  @Path("/version")
  @Produces(MediaType.TEXT_PLAIN)
  @GET
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = String.class))),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Retrieve version of searchbroker (backend)")
  public Response getVersion() {
    String version = new Gson().toJson(ProjectInfo.INSTANCE.getVersionString());
    return addCorsHeaders(Response.ok(version));
  }

  /**
   * Return the directory id of the desired biobanks.
   *
   * @param biobankNameList name of the biobanks
   * @return list of biobank name and their directory ids
   */
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @Path("/getDirectoryID")
  @POST
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = JSONObject[].class)
          )),
      @APIResponse(responseCode = "401", description = "Unauthorized access"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Retrieve biobank- and collection-IDs for list of biobanks")
  public Response getDirectoryID(
      @Parameter(
          name = "biobankNameList",
          description = "The list of biobanks (by name) to get IDs (biobank-ID and collection-ID) "
              + "for",
          example = "['Lübeck', 'Heidelberg']",
          schema = @Schema(type = SchemaType.ARRAY, implementation = String.class))
          List<String> biobankNameList) {
    try {
      JSONArray biobank = new JSONArray();
      for (String biobankName : biobankNameList) {
        JSONObject jsonObject = new JSONObject();
        Site site = SiteUtil.fetchSiteByNameIgnoreCase(biobankName);
        jsonObject.put("biobankId", site.getBiobankid());
        jsonObject.put("collectionId", site.getCollectionid());
        biobank.add(jsonObject);
      }
      return addCorsHeaders(Response.ok(biobank));
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Get OPTIONS-Call for the path "getDirectoryID".
   *
   * @return OPTIONS response.
   */
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @Path("/getDirectoryID")
  @OPTIONS
  @APIResponses({
      @APIResponse(responseCode = "204", description = "no-content")
  })
  @Operation(summary = "The list of biobanks (by name) to get IDs (biobank-ID and collection-ID) "
      + "for (OPTIONS for CORS)")
  public Response getDirectoryIdOptions() {
    try {
      return createPreflightCorsResponse(HttpMethod.POST,
          "origin, Accept, Content-type, Authorization");
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }


  /**
   * Return the directory id of the biobanks.
   *
   * @return list of biobank name and their directory ids
   */
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/allDirectoryIds")
  @GET
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = JSONObject[].class)
          )),
      @APIResponse(responseCode = "401", description = "Unauthorized access"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Retrieve biobank- and collection-IDs for list of biobanks")
  public Response getDirectoryId() {
    try {
      return addCorsHeaders(Response.ok(SiteController.getAllSites()));
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Get OPTIONS-Call for the path "getDirectoryID".
   *
   * @return OPTIONS response.
   */
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/allDirectoryIds")
  @OPTIONS
  @APIResponses({
      @APIResponse(responseCode = "204", description = "no-content")
  })
  @Operation(summary = "Retrieve biobank- and collection-IDs for list of biobanks) "
      + "for (OPTIONS for CORS)")
  public Response getAllDirectoryIdOptions() {
    try {
      return createPreflightCorsResponse(HttpMethod.GET,
          "origin, Accept, Content-type");
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Return the inquiry by id.
   *
   * @param id the id of the inquiry
   * @return the inquiry
   */
  @Secured
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/getInquiry")
  @POST
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "401", description = "Unauthorized access"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getInquiry(int id) {
    try {
      Inquiry inquiry = InquiryUtil.fetchInquiryById(id);
      if (inquiry == null) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }

      if (inquiry.getAuthorId().equals(authenticatedUser.getId())) {
        return Response.ok(gson.toJson(inquiry)).build();
      }

      return Response.status(Response.Status.UNAUTHORIZED).build();

    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Gets the inquiry with the given id.
   *
   * @param authorizationHeader the api key of the client
   * @param userAgentHeader     the user agent of the client
   * @param xmlNamespaceHeader  optional header with xml namespace
   * @param inquiryId           the requested inquiry id
   * @return <CODE>200</CODE> and the serialized inquiry on success
   *        <CODE>400</CODE> if the inquiry id can't be parsed
   *        <CODE>401</CODE> if the provided credentials do not allow to read this inquiry
   *        <CODE>404</CODE> if no inquiry with this id was found
   *        <CODE>500</CODE> on any other error
   */
  @Path("/inquiries/{inquiryid}")
  @GET
  @Produces(MediaType.APPLICATION_XML)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(mediaType = MediaType.TEXT_PLAIN)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getInquiry(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(HttpHeaders.USER_AGENT) String userAgentHeader,
      @HeaderParam(Constants.HEADER_XML_NAMESPACE) String xmlNamespaceHeader,
      @HeaderParam(Constants.HEADER_KEY_QUERY_LANGUAGE) @DefaultValue("QUERY") String queryLanguage,
      @PathParam("inquiryid") int inquiryId) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to retrieve an inquiry");
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    String ret = inquiryHandler.getInquiry(inquiryId, uriInfo, userAgentHeader, queryLanguage);

    Response response = buildResponse(xmlNamespaceHeader, inquiryId, ret);
    if (response.getStatus() == Response.Status.OK.getStatusCode()) {
      try {
        List<InquirySite> inquirySites = InquirySiteUtil.fetchInquirySitesForInquiryId(inquiryId);
        for (InquirySite is : inquirySites) {
          if (is.getSiteId().equals(BankUtil.getSiteIdForBankId(bankId))) {
            is.setRetrievedAt(SamplyShareUtils.getCurrentSqlTimestamp());
            InquirySiteUtil.updateInquirySite(is);
          }
        }
      } catch (Exception e) {
        // Just catch everything for now
        LOGGER.warn("Could not update InquirySite");
      }
    }

    return response;
  }

  /**
   * Gets the name of the searchbroker as given in the config file.
   *
   * @return the name
   */
  @Path("/name")
  @GET
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(mediaType = MediaType.TEXT_PLAIN)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Retrieve name of project (e.g. DKTK)")
  public Response getName() {
    return Response.ok(ProjectInfo.INSTANCE.getConfig().getProperty(CONFIG_PROPERTY_BROKER_NAME))
        .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
  }

  /**
   * Get query from UI.
   *
   * @param json the query
   * @return 202 or 500 code
   */
  @POST
  @Path("/sendQuery")
  @Produces(MediaType.TEXT_PLAIN)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = String.class))),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Save query in searchbroker database")
  public Response sendQuery(
      @Parameter(
          name = "json",
          description = "Query as a JSON object",
          schema = @Schema(implementation = EssentialSimpleQueryDto.class))
          String json,
      @Parameter(
          name = "ntoken",
          description = "The nToken used by the searchbroker and negotiator to identify a query. "
              + "(format: \"UUID(v4)__search_UUID(v4)\")",
          example =
              "4b30d418-d1a0-4915-9f3c-b6d83b75c68a__search_890536a1-7cd5-470f-960d-18afd47499da",
          schema = @Schema(implementation = String.class))
      @QueryParam("ntoken") String ntoken) {
    LOGGER.info("sendQuery called");

    SearchController.releaseQuery(json, ntoken, authenticatedUser);

    LOGGER.info("sendQuery with ntoken '" + ntoken + "'is sent");
    Response.ResponseBuilder responseBuilder = Response.accepted(ntoken);
    return addCorsHeaders(responseBuilder);
  }

  /**
   * Get query from UI.
   *
   * @return 200 or 500 code
   */
  @OPTIONS
  @Path("/sendQuery")
  @Produces(MediaType.TEXT_PLAIN)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(responseCode = "204", description = "no-content")
  })
  @Operation(summary = "Save query in searchbroker database (OPTIONS for CORS)")
  public Response sendQueryOptions() {
    LOGGER.info("sendQuery called (OPTIONS)");
    return createPreflightCorsResponse(HttpMethod.POST, "origin, accept, content-type");
  }

  /**
   * Get the query by the nToken.
   *
   * @param ntoken the nToken
   * @return the query.
   */
  @GET
  @Path("/getQuery")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Retrieve query from searchbroker backend")
  public Response getQuery(
      @Parameter(
          name = "ntoken",
          description = "The nToken used by the searchbroker and negotiator to identify a query."
              + " (format: \"UUID(v4)__search_UUID(v4)\")",
          example =
              "4b30d418-d1a0-4915-9f3c-b6d83b75c68a__search_890536a1-7cd5-470f-960d-18afd47499da",
          schema = @Schema(implementation = String.class))
      @QueryParam("ntoken")
      @DefaultValue("") String ntoken) {
    LOGGER.info("getQuery called");
    String query = new NTokenHandler().findLatestQuery(ntoken);

    return addCorsHeaders(Response.ok(query));
  }

  /**
   * Gets the query part of the inquiry with the given id.
   *
   * @param authorizationHeader the authorization header
   * @param xmlNamespaceHeader  optional header with xml namespace
   * @param inquiryId           the requested inquiry id
   * @return <CODE>200</CODE> and the serialized query of the inquiry on success
   *        <CODE>400</CODE> if the inquiry id can't be parsed
   *        <CODE>401</CODE> if the provided credentials do not allow to read this inquiry
   *        <CODE>404</CODE> if no inquiry with this id was found
   *        <CODE>500</CODE> on any other error
   */
  @Path("/inquiries/{inquiryid}/query")
  @GET
  @Produces(MediaType.APPLICATION_XML)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(mediaType = MediaType.TEXT_PLAIN)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getQuery(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(Constants.HEADER_XML_NAMESPACE) String xmlNamespaceHeader,
      @PathParam("inquiryid") int inquiryId) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to retrieve an inquiry");
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    String ret = inquiryHandler.getQuery(inquiryId);
    return buildResponse(xmlNamespaceHeader, inquiryId, ret);
  }

  @OPTIONS
  @Path("/getQuery")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(responseCode = "204", description = "no-content")
  })
  @Operation(summary = "Retrieve query from searchbroker backend (OPTIONS for CORS)")
  public Response getQueryOptions(@QueryParam("ntoken") @DefaultValue("") String ntoken) {
    LOGGER.info("getQuery called (OPTIONS)");
    return createPreflightCorsResponse(HttpMethod.GET, "origin, accept");
  }

  /**
   * Get result of the bridgeheads.
   *
   * @param id the id of the query
   * @return the result as JSON String
   */
  @Secured
  @GET
  @Path("/getReply")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = Reply[].class))),
      @APIResponse(responseCode = "401", description = "Unauthorized access"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Retrieve detailed reply - data per biobank")
  public Response getReply(
      @Parameter(
          name = "id",
          description = "The ID of the query",
          example = "4711",
          schema = @Schema(implementation = Integer.class))
      @QueryParam("id")
      @DefaultValue("-1") int id,
      @Parameter(
          name = "ntoken",
          description =
              "The nToken used by the searchbroker and negotiator to identify a query. "
                  + "(format: \"UUID(v4)__search_UUID(v4)\")"
                  +
                  "If no ID is provided (or is less than 0) than the nToken is used to identify the"
                  + " query - otherwise the ID itself is used.",
          example =
              "4b30d418-d1a0-4915-9f3c-b6d83b75c68a__search_890536a1-7cd5-470f-960d-18afd47499da",
          schema = @Schema(implementation = String.class))
      @QueryParam("ntoken")
      @DefaultValue("") String ntoken) {
    int usedId = id;
    if (id < 0 && !StringUtils.isEmpty(ntoken)) {
      usedId = new NTokenHandler().findLatestInquiryId(ntoken);
    }

    JSONObject reply = SearchController.getReplysFromQuery(usedId, false);
    Response.ResponseBuilder responseBuilder = Response.ok(reply);
    return addCorsHeaders(responseBuilder);
  }

  @OPTIONS
  @Path("/getReply")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(responseCode = "204", description = "no-content")
  })
  @Operation(summary = "Retrieve detailed reply - data per biobank (OPTIONS for CORS)")
  public Response getReplyOptions() {
    return createPreflightCorsResponse(HttpMethod.GET, "origin, accept, authorization");
  }

  /**
   * Get aggregated result of the bridgeheads (without login).
   *
   * @param id the id of the query
   * @return the result as JSON String
   */
  @GET
  @Path("/getAnonymousReply")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(
              mediaType = MediaType.APPLICATION_JSON,
              schema = @Schema(implementation = Reply[].class))),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Retrieve anonymous reply - only aggregated numbers")
  public Response getAnonymousReply(
      @Parameter(
          name = "id",
          description = "The ID of the query",
          example = "4711",
          schema = @Schema(implementation = Integer.class))
      @QueryParam("id")
      @DefaultValue("-1") int id,
      @Parameter(
          name = "ntoken",
          description =
              "The nToken used by the searchbroker and negotiator to identify a query."
                  + "(format: \"UUID(v4)__search_UUID(v4)\")"
                  +
                  "If no ID is provided (or is less than 0) than the nToken is used to identify the"
                  + " query - otherwise the ID itself is used.",
          example =
              "4b30d418-d1a0-4915-9f3c-b6d83b75c68a__search_890536a1-7cd5-470f-960d-18afd47499da",
          schema = @Schema(implementation = String.class))
      @QueryParam("ntoken")
      @DefaultValue("") String ntoken) {
    int usedId = id;
    if (id < 0 && !StringUtils.isEmpty(ntoken)) {
      usedId = new NTokenHandler().findLatestInquiryId(ntoken);
    }

    JSONObject reply = SearchController.getReplysFromQuery(usedId, true);
    Response.ResponseBuilder responseBuilder = Response.ok(reply);
    return addCorsHeaders(responseBuilder);
  }

  @OPTIONS
  @Path("/getAnonymousReply")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(responseCode = "204", description = "no-content")
  })
  @Operation(summary = "Retrieve anonymous reply - only aggregated numbers (OPTIONS for CORS")
  public Response getAnonymousReplyOptions(
      @Parameter(
          name = "id",
          description = "The ID of the query",
          example = "4711",
          schema = @Schema(implementation = Integer.class))
      @QueryParam("id")
      @DefaultValue("-1") int id,
      @Parameter(
          name = "ntoken",
          description =
              "The nToken used by the searchbroker and negotiator to identify a query. "
                  + "(format: \"UUID(v4)__search_UUID(v4)\")"
                  +
                  "If no ID is provided (or is less than 0) than the nToken is used to identify the"
                  + " query - otherwise the ID itself is used.",
          example =
              "4b30d418-d1a0-4915-9f3c-b6d83b75c68a__search_890536a1-7cd5-470f-960d-18afd47499da",
          schema = @Schema(implementation = String.class))
      @QueryParam("ntoken")
      @DefaultValue("") String ntoken) {
    return createPreflightCorsResponse(HttpMethod.GET, "origin, accept");
  }

  /**
   * Get the count of the sites.
   *
   * @return the count of the sites
   */
  @GET
  @Path("/getSize")
  @Produces(MediaType.TEXT_PLAIN)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(
              mediaType = MediaType.TEXT_PLAIN,
              schema = @Schema(implementation = Integer.class))),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Retrieve number of actively participating biobanks")
  public Response getSize() {
    long size = SiteUtil.fetchSites().stream().filter(Site::getActive).count();

    Response.ResponseBuilder responseBuilder = Response.ok(size);
    return addCorsHeaders(responseBuilder);
  }

  @OPTIONS
  @Path("/getSize")
  @Produces(MediaType.TEXT_PLAIN)
  @Consumes({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
  @APIResponses({
      @APIResponse(responseCode = "204", description = "no-content")
  })
  @Operation(summary = "Retrieve number of actively participating biobanks")
  public Response getSizeOptions() {
    return createPreflightCorsResponse(HttpMethod.GET, "");
  }

  /**
   * Handle registration and activation of a new bank.
   *
   * @param email          the email of the new bank
   * @param authCodeHeader the auth code header
   * @return <CODE>201</CODE> if a new bank was registered
   *        <CODE>400</CODE> if an invalid email was entered
   *        <CODE>401</CODE> if an invalid request token was provided
   */
  @Path("/banks/{email}")
  @PUT
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Possibly unused")
  public Response handlePutBank(@PathParam("email") String email,
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authCodeHeader,
      @HeaderParam("Accesstoken") String accessToken) {
    Response response;
    Response.Status responseStatus;
    String tokenId = "";

    String authCode = SamplyShareUtils
        .getAuthCodeFromHeader(authCodeHeader, Constants.REGISTRATION_HEADER_VALUE);

    if (!SamplyShareUtils.isEmail(email)) {
      LOGGER.warn("Registration attempted with invalid email: " + email);
      responseStatus = Response.Status.BAD_REQUEST;
    } else if (authCode != null && authCode.length() > 0) {
      String locationId = null;
      if (accessToken != null && accessToken.length() > 0) {
        locationId = Utils.getLocationIdFromAccessToken(accessToken);
        LOGGER.debug("location id = " + locationId);
      }
      tokenId = bankRegistration.activate(email, authCode, locationId);
      if ("error".equals(tokenId)) {
        LOGGER.warn("Activation attempted with invalid credentials from: " + email);
        responseStatus = Response.Status.UNAUTHORIZED;
      } else {
        LOGGER.info("Bank activated: " + email);
        responseStatus = Response.Status.CREATED;
      }
    } else {
      responseStatus = bankRegistration.register(email);
    }

    if (responseStatus == Response.Status.CREATED) {
      response = Response.status(responseStatus).entity(tokenId).build();
    } else {
      response = Response.status(responseStatus)
          .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
      if (responseStatus == Response.Status.UNAUTHORIZED) {
        response.getMetadata()
            .add(HttpHeaders.WWW_AUTHENTICATE, Constants.REGISTRATION_AUTH_HEADER_VALUE);
      }
    }
    return response;
  }

  /**
   * Delete a bank from the database.
   *
   * @param email          the email of the bank to delete
   * @param authCodeHeader the auth code header
   * @return <CODE>204</CODE> on success
   *        <CODE>401</CODE> if the credentials don't belong to this bank
   *        <CODE>404</CODE> if no bank was found for this email address
   */
  @Path("/banks/{email}")
  @DELETE
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Possibly unused")
  public Response handleDeleteBank(@PathParam("email") String email,
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authCodeHeader) {
    Response response;
    Response.Status responseStatus;

    String authCode = SamplyShareUtils.getAuthCodeFromHeader(authCodeHeader, "Samply ");
    responseStatus = bankRegistration.delete(email, authCode);
    response = Response.status(responseStatus)
        .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
    return response;
  }

  /**
   * Get the registration status of a bank.
   *
   * @param email          the email of the bank to check
   * @param authCodeHeader the api key header
   * @return <CODE>200</CODE> if email and authcode are ok
   *        <CODE>202</CODE> if registration is started but waiting for confirmation via token
   *        <CODE>403</CODE> if email is registered but authcode is wrong
   *        <CODE>404</CODE> if email is unknown
   */
  @Path("/banks/{email}/status")
  @GET
  @Produces
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(summary = "Possibly unused")
  public Response getRegistrationstatus(@PathParam("email") String email,
      @HeaderParam(HttpHeaders.AUTHORIZATION) String authCodeHeader) {
    LOGGER.debug("Get Status is called for " + email);
    Response response;
    String authCode = SamplyShareUtils.getAuthCodeFromHeader(authCodeHeader, "Samply ");

    Response.Status responseStatus = bankRegistration.checkStatus(email, authCode);

    String siteString;

    try {
      int bankId = Utils.getBankId(authCodeHeader);
      BankSite bankSite = BankSiteUtil.fetchBankSiteByBankId(bankId);

      Site site = SiteUtil.fetchSiteById(bankSite.getSiteId());
      SiteInfo siteInfo = Utils.siteToSiteInfo(site);
      siteInfo.setApproved(bankSite.getApproved());

      Gson gson = new Gson();
      siteString = gson.toJson(siteInfo);
      response = Response.status(responseStatus).entity(siteString)
          .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
    } catch (Exception e) {
      LOGGER.warn("Could not get site...no need to worry though." + e);
      response = Response.status(responseStatus)
          .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
    }

    return response;
  }

  /**
   * Gets the list of inquiries. Only get inquiries that are linked with the requesting bank (site)
   * and are not expired, yet. Also send client version information to icinga on every 12th call
   * (~each minute).
   *
   * @param authorizationHeader the authorization header
   * @param userAgent           user agent of the requesting client
   * @param xmlNamespaceHeader  optional header with xml namespace
   * @return <CODE>200</CODE> and a serialized list of inquiry ids/revisions on success
   *        <CODE>401</CODE> if no bank is found for the supplied api key
   *        <CODE>500</CODE> on any other error
   */
  @Path("/inquiries")
  @GET
  @Produces(MediaType.APPLICATION_XML)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(mediaType = MediaType.TEXT_PLAIN)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getInquiries(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(HttpHeaders.USER_AGENT) @DefaultValue("") String userAgent,
      @HeaderParam(Constants.HEADER_XML_NAMESPACE) String xmlNamespaceHeader) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to retrieve inquiry list");
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    LOGGER.debug("GET /inquiries called from: " + Utils.userAgentAndBankToJson(userAgent, bankId));

    icingaController.asyncSendVersionReportEveryMinute(userAgent, bankId);

    String inquiryList = inquiryHandler.list(bankId);

    if (StringUtils.isEmpty(inquiryList) || inquiryList.equalsIgnoreCase("error")) {
      LOGGER.warn("There was an error while retrieving the list of inquiries");
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
    return Response.ok().entity(inquiryList)
        .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
  }

  /**
   * Gets the ViewFields part of the inquiry with the given id.
   *
   * @param authorizationHeader the authorization header
   * @param xmlNamespaceHeader  optional header with xml namespace
   * @param inquiryId           the requested inquiry id
   * @return <CODE>200</CODE> and the serialized viewfields for the inquiry on success
   *        <CODE>204</CODE> if no viewfields are specified for this inquiry
   *        <CODE>400</CODE> if the inquiry id can't be parsed
   *        <CODE>401</CODE> if the provided credentials do not allow to read this inquiry
   *        <CODE>404</CODE> if no inquiry with this id was found
   *        <CODE>500</CODE> on any other error
   */
  @Path("/inquiries/{inquiryid}/viewfields")
  @GET
  @Produces(MediaType.APPLICATION_XML)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(mediaType = MediaType.APPLICATION_XML)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getViewFields(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(Constants.HEADER_XML_NAMESPACE) String xmlNamespaceHeader,
      @PathParam("inquiryid") int inquiryId) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to retrieve an inquiry");
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    String ret = inquiryHandler.getViewFields(inquiryId);
    if (StringUtils.isEmpty(ret)) {
      LOGGER.debug("No ViewFields were set for inquiry with id " + inquiryId);
      return Response.status(Response.Status.OK).build();
    }
    return buildResponse(xmlNamespaceHeader, inquiryId, ret);
  }

  /**
   * Gets the contact for the inquiry with the given id.
   *
   * @param authorizationHeader the authorization header
   * @param xmlNamespaceHeader  optional header with xml namespace
   * @param inquiryId           the requested inquiry id
   * @return <CODE>200</CODE> and the serialized contact information for the inquiry on success
   *        <CODE>400</CODE> if the inquiry id can't be parsed
   *        <CODE>401</CODE> if the provided credentials do not allow to read this inquiry
   *        <CODE>404</CODE> if no inquiry with this id was found
   *        <CODE>500</CODE> on any other error
   */
  @Path("/inquiries/{inquiryid}/contact")
  @GET
  @Produces(MediaType.APPLICATION_XML)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(mediaType = MediaType.APPLICATION_XML)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getContact(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(Constants.HEADER_XML_NAMESPACE) String xmlNamespaceHeader,
      @PathParam("inquiryid") int inquiryId) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to retrieve a contact");
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    String ret;
    try {
      ret = inquiryHandler.getContact(inquiryId);
    } catch (JAXBException e) {
      LOGGER.error("Error getting contact for inquiry id " + inquiryId);
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    return buildResponse(xmlNamespaceHeader, inquiryId, ret);
  }


  /**
   * Gets the additional description for the inquiry with the given id.
   *
   * @param authorizationHeader the authorization header
   * @param xmlNamespaceHeader  optional header with xml namespace
   * @param inquiryId           the requested inquiry id
   * @return <CODE>200</CODE> and the serialized details for the inquiry on success
   *        <CODE>400</CODE> if the inquiry id can't be parsed
   *        <CODE>401</CODE> if the provided credentials do not allow to read this inquiry
   *        <CODE>404</CODE> if no inquiry with this id was found
   *        <CODE>500</CODE> on any other error
   */
  @Path("/inquiries/{inquiryid}/info")
  @GET
  @Produces(MediaType.APPLICATION_XML)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(mediaType = MediaType.APPLICATION_XML)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getInfo(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(Constants.HEADER_XML_NAMESPACE) String xmlNamespaceHeader,
      @PathParam("inquiryid") int inquiryId) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to retrieve a contact");
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    String ret;
    try {
      ret = inquiryHandler.getInfo(inquiryId);
    } catch (JAXBException e) {
      LOGGER.error("Error getting info for inquiry id " + inquiryId);
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    return buildResponse(xmlNamespaceHeader, inquiryId, ret);
  }

  /**
   * Check if an expose is present for the given inquiry.
   *
   * @param inquiryId the inquiry id to check
   * @return <CODE>200</CODE> if the inquiry has an expose
   *        <CODE>400</CODE> if the inquiry id can't be parsed
   *        <CODE>404</CODE> if no expose is available for this inquiry
   */
  @Path("/inquiries/{inquiryid}/hasexpose")
  @GET
  @Produces(MediaType.TEXT_PLAIN)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok",
          content = @Content(mediaType = MediaType.TEXT_PLAIN)),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response isSynopsisAvailable(@PathParam("inquiryid") int inquiryId) {
    Document expose = DocumentUtil.fetchExposeByInquiryId(inquiryId);

    if (expose == null || expose.getData() == null || expose.getData().length < 1) {
      return Response.status(Response.Status.NOT_FOUND).entity("unavailable")
          .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
    } else {
      return Response.status(Response.Status.OK).build();
    }
  }

  /**
   * Store reply for an inquiry by a bank.
   *
   * @param authorizationHeader the authorization header
   * @param inquiryId           the inquiry id
   * @param bankEmail           the bank email
   * @param reply               the reply content
   * @return <CODE>200</CODE> on success
   *        <CODE>400</CODE> if reply is empty
   *        <CODE>401</CODE> if the provided credentials do not allow to access this inquiry
   *        <CODE>404</CODE> if no inquiry with this id was found
   *        <CODE>500</CODE> on any other error
   */
  @Path("/inquiries/{inquiryid}/replies/{bankemail}")
  @PUT
  @Consumes(MediaType.TEXT_PLAIN)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response putReply(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @PathParam("inquiryid") int inquiryId,
      @PathParam("bankemail") String bankEmail,
      String reply) {
    Timestamp timestamp = SamplyShareUtils.getCurrentSqlTimestamp();
    int bankId = Utils.getBankId(authorizationHeader, bankEmail);

    if (bankId < 0) {
      LOGGER.warn("Unauthorized attempt to answer to an inquiry from " + bankEmail);
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    if (reply == null || reply.length() < 1) {
      LOGGER.warn("Rejecting empty reply to inquiry from " + bankEmail);
      return Response.status(Response.Status.BAD_REQUEST).build();
    }

    if (!inquiryHandler.saveReply(inquiryId, bankId, reply, timestamp)) {
      LOGGER.warn("An error occurred while trying to store a reply to inquiry " + inquiryId + " by "
          + bankEmail);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }

    LOGGER.info("Stored reply to inquiry " + inquiryId + " from " + bankEmail);
    return Response.ok().header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
  }


  /**
   * Get the expose file for an inquiry.
   *
   * @param authKeyHeader the api key
   * @param inquiryId     the id of the inquiry for which the expose shall be gotten
   * @return <CODE>200</CODE> and the expose on success
   *        <CODE>400</CODE> if the inquiry id could not be parsed
   *        <CODE>404</CODE> if no inquiry with this id was found
   */
  @Path("/exposes/{inquiryid}")
  @GET
  @Produces(CONTENT_TYPE_PDF)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getSynopsis(@HeaderParam(HttpHeaders.AUTHORIZATION) String authKeyHeader,
      @PathParam("inquiryid") int inquiryId) {
    Document expose = DocumentUtil.fetchExposeByInquiryId(inquiryId);

    if (expose == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
    } else {
      return Response.ok()
          .header("Content-Disposition", "attachment; filename=" + expose.getFilename())
          .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).entity(expose.getData())
          .build();
    }
  }

  /**
   * Get the expose file for an inquiry (alias).
   *
   * @param authKeyHeader the api key
   * @param inquiryId     the id of the inquiry for which the expose shall be gotten
   * @return <CODE>200</CODE> and the expose on success
   *        <CODE>400</CODE> if the inquiry id could not be parsed
   *        <CODE>404</CODE> if no inquiry with this id was found
   */
  @Path("/inquiries/{inquiryid}/expose")
  @GET
  @Produces(CONTENT_TYPE_PDF)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getSynopsisAlias(@HeaderParam(HttpHeaders.AUTHORIZATION) String authKeyHeader,
      @PathParam("inquiryid") int inquiryId) {
    return this.getSynopsis(authKeyHeader, inquiryId);
  }

  /**
   * Gets the list of sites available. This does not guarantee that each site has a registered
   * bank.
   *
   * @param authorizationHeader the authorization header
   * @return <CODE>200</CODE> and the list of sites on success
   *        <CODE>401</CODE> on authorization error
   *        <CODE>500</CODE> on any other error
   */
  @Path("/sites")
  @GET
  @Produces(MediaType.APPLICATION_XML)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getSites(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(HttpHeaders.USER_AGENT) String userAgent,
      @Context HttpServletRequest request) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to retrieve list of sites from " + request.getRemoteAddr());
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    List<Site> sites = SiteUtil.fetchSites();
    String returnValue;

    Gson gson = new Gson();

    try {
      returnValue = gson.toJson(sites);
    } catch (Exception e) {
      LOGGER.warn("Error trying to return site list: " + e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }

    return Response.ok().entity(returnValue)
        .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
  }

  /**
   * Gets the list of site names available. This does not guarantee that each site has a registered
   * bank.
   *
   * @param authorizationHeader the authorization header
   * @return <CODE>200</CODE> and the list of site names on success
   *        <CODE>401</CODE> on authorization error
   *        <CODE>500</CODE> on any other error
   */
  @Path("/siteNames")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  public Response getSitesName(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(HttpHeaders.USER_AGENT) String userAgent,
      @Context HttpServletRequest request) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to retrieve list of sites from " + request.getRemoteAddr());
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      List<Site> sitesList = SiteUtil.fetchSites();
      JsonArray sitesJsonArray = new JsonArray();
      for (Site site : sitesList) {
        JsonObject siteJson = new JsonObject();
        siteJson.addProperty("name", site.getName());
        sitesJsonArray.add(siteJson);
      }
      String array = sitesJsonArray.toString();
      return Response.ok().entity(array)
          .header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
    } catch (Exception e) {
      LOGGER.warn("Error trying to return site list: " + e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Set the site for a bank.
   *
   * @param authorizationHeader the authorization header
   * @param userAgent           the user agent of the client
   * @param request             the http request
   * @param siteName            the name of the site to set
   * @return <CODE>200</CODE> on success
   *        <CODE>401</CODE> on authorization error
   *        <CODE>404</CODE> if the site or bank could not be found
   */
  @Path("/banks/{email}/site/{siteName}")
  @PUT
  @APIResponses({
      @APIResponse(
          responseCode = "200",
          description = "ok"),
      @APIResponse(responseCode = "500", description = "Internal Server Error")
  })
  @Operation(description = "Possibly unused")
  public Response setSiteByName(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      @HeaderParam(HttpHeaders.USER_AGENT) String userAgent,
      @Context HttpServletRequest request,
      @PathParam("siteName") String siteName) {
    int bankId = Utils.getBankId(authorizationHeader);
    if (isBankUnauthorized(bankId)) {
      LOGGER.warn("Unauthorized attempt to set a site from " + request.getRemoteAddr());
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    try {
      SiteController.setSiteForBank(siteName, bankId);
      return Response.ok().header(Constants.SERVER_HEADER_KEY, serverHeaderValue).build();
    } catch (NotAllowedException e) {
      return Response.status(405, e.getMessage()).header(Constants.SERVER_HEADER_KEY,
          serverHeaderValue).build();
    } catch (AlreadyExistsException e) {
      return Response.status(409, e.getMessage()).header(Constants.SERVER_HEADER_KEY,
          serverHeaderValue).build();
    }
  }



  /**
   * Build a Response object depending on the given value.
   *
   * @param xmlNamespace desired output namespace (will be converted to lower case)
   * @param inquiryId    id of the inquiry
   * @param ret          the payload
   * @return the Response to send back to the client
   */
  private Response buildResponse(String xmlNamespace, int inquiryId, String ret) {
    if (ret.equalsIgnoreCase("error")) {
      LOGGER.warn("Could not get inquiry");
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    } else if (ret.equalsIgnoreCase("notFound")) {
      LOGGER.warn("Inquiry with id " + inquiryId + " not found");
      return Response.status(Response.Status.NOT_FOUND).build();
    }

    ret = Utils.fixNamespaces(ret, xmlNamespace);
    return Response.ok().entity(ret).header(Constants.SERVER_HEADER_KEY, serverHeaderValue)
        .build();
  }

  private boolean isBankUnauthorized(int bankId) {
    return bankId < 0;
  }

  private Response addCorsHeaders(Response.ResponseBuilder builder) {
    return builder
        .header("Access-Control-Allow-Origin", "*")
        .header("Cache-Control", "no-cache")
        .build();
  }

  private Response createPreflightCorsResponse(String allowedMethod, String allowedHeaders) {
    return Response.noContent()
        .header("Access-Control-Allow-Origin", "*")
        .header("Access-Control-Allow-Methods", allowedMethod)
        .header("Access-Control-Allow-Headers", allowedHeaders)
        .build();
  }
}
