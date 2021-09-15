package de.samply.share.broker.rest;

import static de.samply.share.broker.rest.InquiryHandler.isQueryLanguageCql;
import static de.samply.share.broker.rest.InquiryHandler.isQueryLanguageViewQuery;

import com.google.gson.Gson;
import de.samply.common.mdrclient.MdrClient;
import de.samply.share.broker.control.SearchController;
import de.samply.share.broker.model.db.tables.pojos.Site;
import de.samply.share.broker.monitoring.QueryObject;
import de.samply.share.broker.utils.Utils;
import de.samply.share.broker.utils.connector.IcingaConnector;
import de.samply.share.broker.utils.connector.IcingaConnectorException;
import de.samply.share.broker.utils.db.BankUtil;
import de.samply.share.broker.utils.db.DbUtils;
import de.samply.share.broker.utils.db.TokenRequestUtil;
import de.samply.share.common.model.dto.monitoring.StatusReportItem;
import de.samply.share.common.utils.Constants;
import de.samply.share.common.utils.ProjectInfo;
import de.samply.share.model.common.And;
import de.samply.share.model.common.Attribute;
import de.samply.share.model.common.Eq;
import de.samply.share.model.common.ObjectFactory;
import de.samply.share.model.common.Or;
import de.samply.share.model.common.Query;
import de.samply.share.model.common.Where;
import de.samply.share.utils.QueryConverter;
import de.samply.web.mdrfaces.MdrContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;


/**
 * Handle incoming data that shall be relayed to icinga and active checks by icinga itself.
 */
@Path("/monitoring")
public class Monitoring {

  private Logger logger = LogManager.getLogger(this.getClass().getName());

  /**
   * Construct a reference query to use for the monitoring system.
   * TODO: Maybe put this into the database for easy modification
   *
   * @return string representation of the reference query
   */
  private static Query createReferenceQuery() {
    ObjectFactory objectFactory = new ObjectFactory();

    Or or = new Or();
    Eq eq = new Eq();
    Attribute attribute = new Attribute();

    if (ProjectInfo.INSTANCE.getProjectName().toLowerCase().equals("samply")) {
      attribute.setMdrKey("urn:mdr16:dataelement:23:1");
      attribute.setValue(objectFactory.createValue("female"));

    } else if (ProjectInfo.INSTANCE.getProjectName().toLowerCase().equals("dktk")) {
      // TNM-T = 2
      attribute.setMdrKey("urn:dktk:dataelement:100:*");
      attribute.setValue(objectFactory.createValue("2"));
    }
    eq.setAttribute(attribute);
    or.getAndOrEqOrLike().add(eq);
    And and = new And();
    and.getAndOrEqOrLike().add(or);
    Where where = new Where();
    where.getAndOrEqOrLike().add(and);
    Query query = new Query();
    query.setWhere(where);

    return query;
  }

  /**
   * Respond to an active check from icinga.
   * Check if the database is reachable Check if the MDR is reachable.
   *
   * @return health status for icinga to display
   */
  @Path("/check")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response checkStatus() {
    // Use the status check from icinga as a trigger to delete old token requests
    TokenRequestUtil.deleteOldTokenRequests();

    StringBuilder stringBuilder = new StringBuilder();

    logger.debug("Checking DB Connection");
    if (!DbUtils.checkConnection()) {
      stringBuilder.append("dbConnection_error");
    }

    logger.debug("Checking MDR Connection");
    try {
      MdrClient mdrClient = MdrContext.getMdrContext().getMdrClient();
      mdrClient.getNamespaces("en");
    } catch (ExecutionException e) {
      stringBuilder.append("mdrConnection_error");
    }

    if (stringBuilder.length() < 1) {
      stringBuilder.append("ok");
    }

    logger.debug("Checking status");
    Status status = new Status();
    status.setStatus(stringBuilder.toString());

    Gson gson = new Gson();
    return Response.ok(gson.toJson(status), MediaType.APPLICATION_JSON).build();
  }

  /**
   * Handle incoming monitoring information from a connected samply share client.
   *
   * @param authorizationHeader the api key
   * @param statusReport        the list of items to report to icinga
   * @return <CODE>200</CODE> on success
   *        <CODE>401</CODE> if no bank was found to the api key
   *        <CODE>500</CODE> on any other error
   */
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response putStatus(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorizationHeader,
      List<StatusReportItem> statusReport) {

    int bankId = Utils.getBankId(authorizationHeader);

    if (bankId < 0) {
      logger.warn("Unauthorized attempt to put status report");
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Site site = BankUtil.getSiteForBankId(bankId);

    if (site == null) {
      logger.warn("Site not found for status report. Bank ID " + bankId);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }

    try {
      IcingaConnector.reportStatusItems(site.getName(), statusReport);
      return Response.ok().build();
    } catch (IcingaConnectorException e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
    }
  }

  /**
   * Provide a reference query for samply share clients to execute in order to transmit monitoring
   * information.
   *
   * @return the serialized reference query
   */
  @Path("/referencequery")
  @GET
  @Produces(MediaType.APPLICATION_XML)
  public Response getReferenceQuery(
      @HeaderParam(Constants.HEADER_KEY_QUERY_LANGUAGE)
      @DefaultValue("QUERY") String queryLanguage) {
    try {
      String queryString = "";
      if (isQueryLanguageViewQuery(queryLanguage)) {
        Query query = createReferenceQuery();
        queryString = QueryConverter.queryToXml(query);
      } else if (isQueryLanguageCql(queryLanguage)) {
        queryString = createReferenceQueryCql();
      }
      return Response.ok(queryString).build();
    } catch (JAXBException e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }

  }

  /**
   * Create a new monitoring query.
   * @param queryObject the query as cql string and the target sites as list
   * @return response if the query has been created
   */
  @Path("queries")
  @POST
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
  @Operation(summary = "Save query in searchbroker database for monitoring")
  public Response createQuery(@HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
      @Parameter(
          name = "query",
          description = "Query as a JSON object",
          schema = @Schema(implementation = QueryObject.class))
          QueryObject queryObject) {
    try {
      if (auth == null || !checkBasicAuth(auth)) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      return Response.ok(
          SearchController.releaseQuery(new Gson().toJson(queryObject.getCqlQueryList()),
              queryObject.getTarget())).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Get the results for a monitoring query.
   * @param auth basic auth
   * @param queryId the query id
   * @return the results of the sites
   */
  @Path("queries/{id}")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getResult(@HeaderParam(HttpHeaders.AUTHORIZATION) String auth,
      @PathParam("id") int queryId) {
    try {
      if (auth == null || !checkBasicAuth(auth)) {
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      return Response.ok(SearchController.getResultFromQuery(queryId)).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  private String createReferenceQueryCql() {
    return "library Retrieve\n"
        + "using FHIR version '4.0.0'\n"
        + "include FHIRHelpers version '4.0.0'\n"
        + "context Patient\n"
        + "define InInitialPopulation:\n"
        + "    Patient.gender = 'female'";
  }

  static class Status {

    private String status;

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }
  }

  private boolean checkBasicAuth(String basicAuth) {
    String username = ProjectInfo.INSTANCE.getConfig().getProperty("icinga.username");
    String password = ProjectInfo.INSTANCE.getConfig().getProperty("icinga.password");
    String base64Credentials = basicAuth.substring("Basic".length()).trim();
    byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
    String credentials = new String(credDecoded, StandardCharsets.UTF_8);
    final String[] values = credentials.split(":", 2);
    return values[0].equals(username) && values[1].equals(password);
  }

}
