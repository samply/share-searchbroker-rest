package de.samply.share.broker.rest;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.samply.share.broker.jdbc.ResourceManager;
import de.samply.share.broker.model.db.Tables;
import de.samply.share.broker.model.db.enums.ActionType;
import de.samply.share.broker.model.db.enums.DocumentType;
import de.samply.share.broker.model.db.enums.InquiryCriteriaType;
import de.samply.share.broker.model.db.enums.InquiryStatus;
import de.samply.share.broker.model.db.enums.ProjectStatus;
import de.samply.share.broker.model.db.tables.daos.ContactDao;
import de.samply.share.broker.model.db.tables.daos.InquiryDao;
import de.samply.share.broker.model.db.tables.daos.ReplyDao;
import de.samply.share.broker.model.db.tables.daos.UserDao;
import de.samply.share.broker.model.db.tables.pojos.Action;
import de.samply.share.broker.model.db.tables.pojos.BankSite;
import de.samply.share.broker.model.db.tables.pojos.Document;
import de.samply.share.broker.model.db.tables.pojos.Inquiry;
import de.samply.share.broker.model.db.tables.pojos.InquiryCriteria;
import de.samply.share.broker.model.db.tables.pojos.Project;
import de.samply.share.broker.model.db.tables.pojos.Reply;
import de.samply.share.broker.model.db.tables.pojos.Site;
import de.samply.share.broker.model.db.tables.pojos.User;
import de.samply.share.broker.statistics.StatisticsHandler;
import de.samply.share.broker.utils.EssentialSimpleQueryDto2ShareXmlTransformer;
import de.samply.share.broker.utils.cql.EssentialSimpleQueryDto2CqlTransformer;
import de.samply.share.broker.utils.db.ActionUtil;
import de.samply.share.broker.utils.db.BankSiteUtil;
import de.samply.share.broker.utils.db.ContactUtil;
import de.samply.share.broker.utils.db.DocumentUtil;
import de.samply.share.broker.utils.db.InquiryCriteriaUtil;
import de.samply.share.broker.utils.db.InquiryUtil;
import de.samply.share.broker.utils.db.ProjectUtil;
import de.samply.share.broker.utils.db.SiteUtil;
import de.samply.share.common.utils.Constants;
import de.samply.share.common.utils.ProjectInfo;
import de.samply.share.common.utils.SamplyShareUtils;
import de.samply.share.essentialquery.EssentialSimpleFieldDto;
import de.samply.share.essentialquery.EssentialSimpleQueryDto;
import de.samply.share.essentialquery.EssentialSimpleValueDto;
import de.samply.share.model.common.And;
import de.samply.share.model.common.Contact;
import de.samply.share.model.common.Info;
import de.samply.share.model.common.ObjectFactory;
import de.samply.share.model.common.Query;
import de.samply.share.model.common.Where;
import de.samply.share.model.common.inquiry.InquiriesIdList;
import de.samply.share.model.cql.CqlQuery;
import de.samply.share.model.cql.CqlQueryList;
import de.samply.share.query.enums.SimpleValueCondition;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.tools.json.JSONObject;
import org.jooq.tools.json.JSONParser;
import org.jooq.tools.json.ParseException;

/**
 * The Class InquiryHandler.
 */
public class InquiryHandler {

  private static final Logger logger = LogManager.getLogger(InquiryHandler.class);

  private static final String ENTITY_TYPE_FOR_QUERY = "Donor + Sample";
  private static final String ENTITY_TYPE_FOR_CQL_PATIENT = "Patient";
  private static final String ENTITY_TYPE_FOR_CQL_SPECIMEN = "Specimen";

  private static final String QUERYLANGUAGE_QUERY = "QUERY";
  private static final String QUERYLANGUAGE_CQL = "CQL";
  private static final Gson gson = new Gson();

  public InquiryHandler() {
  }

  static boolean isQueryLanguageViewQuery(String queryLanguage) {
    return StringUtils.equalsIgnoreCase(queryLanguage, QUERYLANGUAGE_QUERY);
  }

  static boolean isQueryLanguageCql(String queryLanguage) {
    return StringUtils.equalsIgnoreCase(queryLanguage, QUERYLANGUAGE_CQL);
  }

  /**
   * Store an inquiry and release it (or wait for ccp office authorization first).
   *
   * @param simpleQueryDtoJson the query criteria of the inquiry as EssentialSimpleQueryDto
   *                           represented as XML
   * @param userid             the id of the user that releases the inquiry
   * @param inquiryName        the label of the inquiry
   * @param inquiryDescription the description of the inquiry
   * @param exposeId           the id of the expose linked with this inquiry
   * @param voteId             the id of the vote linked with this inquiry
   * @param resultTypes        list of the entities that are searched for
   * @param bypassExamination  if true, no check by the ccp office is required
   * @param isMonitoring  if true, no creation of cql query
   * @return the id of the inquiry
   */
  public int storeAndRelease(String simpleQueryDtoJson, int userid, String inquiryName,
      String inquiryDescription, int exposeId, int voteId, List<String> resultTypes,
      boolean bypassExamination, boolean isMonitoring) {
    int inquiryId = store(simpleQueryDtoJson, userid, inquiryName, inquiryDescription,
        exposeId, voteId, resultTypes, isMonitoring);
    Inquiry inquiry = InquiryUtil.fetchInquiryById(inquiryId);
    release(inquiry, bypassExamination);
    return inquiryId;
  }

  private EssentialSimpleQueryDto jsonString2EssentialDto(String simpleQueryDtoJson) {
    EssentialSimpleQueryDto queryDto = new EssentialSimpleQueryDto();

    try {
      Gson gson = new Gson();
      queryDto = gson.fromJson(simpleQueryDtoJson, EssentialSimpleQueryDto.class);
    } catch (Exception e) {
      e.printStackTrace();
    }

    for (EssentialSimpleFieldDto field : queryDto.getFieldDtos()) {
      field.setValueDtos(
          field.getValueDtos().stream()
              .filter(valueDto -> !isEmpty(valueDto))
              .collect(Collectors.toList()));
    }

    queryDto.setFieldDtos(queryDto.getFieldDtos().stream()
        .filter(fieldDto -> !isEmpty(fieldDto))
        .collect(Collectors.toList()));

    return queryDto;
  }

  /**
   * Store a new inquiry draft.
   *
   * @param query      the query criteria of the inquiry
   * @param userid                  the id of the user that releases the inquiry
   * @param inquiryName             the label of the inquiry
   * @param inquiryDescription      the description of the inquiry
   * @param exposeId                the id of the expose linked with this inquiry
   * @param voteId                  the id of the vote linked with this inquiry
   * @param resultTypes             list of the entities that are searched for
   * @param isMonitoring  if true, no creation of cql query
   * @return the id of the inquiry draft
   */
  private int store(String query, int userid, String inquiryName,
      String inquiryDescription, int exposeId, int voteId, List<String> resultTypes,
      boolean isMonitoring) {
    int returnValue = 0;
    UserDao userDao;
    User user;
    Inquiry inquiry;
    String projectName = ProjectInfo.INSTANCE.getProjectName();
    boolean isDktk = projectName.equalsIgnoreCase("dktk");

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);

      userDao = new UserDao(configuration);
      user = userDao.fetchOneById(userid);
      if (user == null) {
        return 0;
      }

      inquiry = new Inquiry();
      inquiry.setAuthorId(user.getId());
      inquiry.setRevision(1);
      inquiry.setLabel(inquiryName);
      inquiry.setDescription(inquiryDescription);

      inquiry.setStatus(InquiryStatus.IS_DRAFT);

      String joinedResultTypes = "";
      if (isDktk) {
        Joiner joiner = Joiner.on(",");
        joinedResultTypes = joiner.join(resultTypes);
      }
      inquiry.setResultType(joinedResultTypes);

      Record inquiryRecord = saveInquiry(inquiry, connection);

      inquiry.setCreated(inquiryRecord.getValue(Tables.INQUIRY.CREATED));
      inquiry.setId(inquiryRecord.getValue(Tables.INQUIRY.ID));

      if (!isMonitoring) {
        EssentialSimpleQueryDto essentialSimpleQueryDto = jsonString2EssentialDto(query);
        createAndSaveInquiryCriteria(essentialSimpleQueryDto, inquiry, connection);
        createAndSaveStatistics(essentialSimpleQueryDto, inquiry.getId());
      } else {
        CqlQueryList cqlQueryList = gson.fromJson(query, CqlQueryList.class);
        for (CqlQuery cqlQuery : cqlQueryList.getQueries()) {
          InquiryCriteria inquiryCriteria = new InquiryCriteria();
          inquiryCriteria.setCriteria(cqlQuery.getCql());
          inquiryCriteria.setInquiryId(inquiry.getId());
          inquiryCriteria.setType(InquiryCriteriaType.IC_CQL);
          inquiryCriteria.setEntityType(cqlQuery.getEntityType());
          saveInquiryCriteria(inquiryCriteria, connection);
        }
      }
      if (exposeId > 0) {
        Document expose = DocumentUtil.getDocumentById(exposeId);
        // TODO: this threw an NPE
        expose.setInquiryId(inquiry.getId());
        DocumentUtil.updateDocument(expose);
      }

      if (voteId > 0) {
        Document vote = DocumentUtil.getDocumentById(voteId);
        vote.setInquiryId(inquiry.getId());
        DocumentUtil.updateDocument(vote);
      }

      returnValue = inquiry.getId();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return returnValue;
  }

  private void createAndSaveStatistics(EssentialSimpleQueryDto essentialSimpleQueryDto,
      Integer inquiryId) {
    StatisticsHandler statisticsHandler = new StatisticsHandler();
    statisticsHandler.save(essentialSimpleQueryDto, inquiryId);
  }

  /**
   * Release an inquiry and spawn a new project if needed.
   *
   * @param inquiry           the inquiry (must be in draft status)
   * @param bypassExamination if the inquiry shall only be sent to the own site, no examination is
   *                          necessary
   */
  private void release(Inquiry inquiry, boolean bypassExamination) {
    if (inquiry == null || !inquiry.getStatus().equals(InquiryStatus.IS_DRAFT)) {
      logger.debug("Tried to release an inquiry that is not a draft. Skipping.");
      return;
    }
    InquiryDao inquiryDao;

    String projectName = ProjectInfo.INSTANCE.getProjectName();
    boolean isDktk = projectName.equalsIgnoreCase("dktk");

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);
      DSLContext dslContext = ResourceManager.getDslContext(connection);

      inquiry.setStatus(InquiryStatus.IS_RELEASED);
      java.sql.Date expiryDate = new java.sql.Date(
          SamplyShareUtils.getCurrentDate().getTime() + InquiryUtil.INQUIRY_TTL);
      inquiry.setExpires(expiryDate);
      inquiry.setCreated(SamplyShareUtils.getCurrentSqlTimestamp());

      if (inquiry.getRevision() < 1) {
        inquiry.setRevision(1);
      }
      boolean createProject = (isDktk && !bypassExamination);
      if (createProject) {
        // Use the inquiry name as a preliminary project name. Has to be changed later when it
        // becomes a project.
        Record record = dslContext
            .insertInto(Tables.PROJECT, Tables.PROJECT.PROJECTLEADER_ID, Tables.PROJECT.STATUS,
                Tables.PROJECT.NAME)
            .values(inquiry.getAuthorId(), ProjectStatus.PS_NEW, inquiry.getLabel())
            .returning(Tables.PROJECT.ID, Tables.PROJECT.APPLICATION_NUMBER).fetchOne();

        int projectId = record.getValue(Tables.PROJECT.ID);
        inquiry.setProjectId(projectId);

        inquiryDao = new InquiryDao(configuration);
        inquiryDao.update(inquiry);

        int inquiryId = inquiry.getId();

        DocumentUtil.setProjectIdForDocumentByInquiryId(inquiryId, projectId);

      } else {
        inquiryDao = new InquiryDao(configuration);
        inquiryDao.update(inquiry);
      }

    } catch (SQLException e) {
      logger.warn("SQL Exception when trying to spawn project.");
    }
  }

  /**
   * Spawn a project for an edited inquiry. Or change the status if needed.
   *
   * @param inquiry     the inquiry
   * @param resultTypes a list of expected result types from the inquiry
   * @param userid      the id of the user that created the inquiry
   */
  public void spawnProjectIfNeeded(Inquiry inquiry, List<String> resultTypes, int userid) {
    UserDao userDao;
    User user;
    InquiryDao inquiryDao;

    String projectName = ProjectInfo.INSTANCE.getProjectName();
    boolean isDktk = projectName.equalsIgnoreCase("dktk");

    if (!isDktk) {
      logger.debug("Not dktk...no project will be spawned");
      return;
    } else if (inquiry.getProjectId() != null) {
      logger.debug("Project is already spawned...change status");
      Project project = ProjectUtil.fetchProjectById(inquiry.getProjectId());
      project.setStatus(ProjectStatus.PS_OPEN_MOREINFO_PROVIDED);
      project.setSeen(false);
      ProjectUtil.updateProject(project);

      Action action = new Action();
      action.setProjectId(project.getId());
      action.setMessage(ActionType.AT_INQUIRY_EDITED.toString());
      action.setType(ActionType.AT_PROJECT_CALLBACK_RECEIVED);
      action.setTime(SamplyShareUtils.getCurrentTime());
      action.setUserId(userid);
      ActionUtil.insertAction(action);
      return;
    }

    logger.debug("Spawning project");

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);

      userDao = new UserDao(configuration);
      user = userDao.fetchOneById(userid);
      if (user == null) {
        return;
      }

      inquiry.setStatus(InquiryStatus.IS_RELEASED);

      String joinedResultTypes = "";
      try {
        Joiner joiner = Joiner.on(",");
        joinedResultTypes = joiner.join(resultTypes);
      } catch (Exception e) {
        logger.warn("couldn't join result types: " + e);

      }
      inquiry.setResultType(joinedResultTypes);
      DSLContext dslContext = ResourceManager.getDslContext(connection);
      // Use the inquiry name as a preliminary project name. Has to be
      // changed later when it becomes a project.
      Record record = dslContext
          .insertInto(Tables.PROJECT, Tables.PROJECT.PROJECTLEADER_ID, Tables.PROJECT.STATUS,
              Tables.PROJECT.NAME)
          .values(inquiry.getAuthorId(), ProjectStatus.PS_NEW, inquiry.getLabel())
          .returning(Tables.PROJECT.ID, Tables.PROJECT.APPLICATION_NUMBER).fetchOne();

      inquiryDao = new InquiryDao(configuration);
      // Reload the inquiry to fill the expires field
      int projectId = record.getValue(Tables.PROJECT.ID);
      inquiry.setProjectId(projectId);
      inquiryDao.update(inquiry);
      int inquiryId = inquiry.getId();
      DocumentUtil.setProjectIdForDocumentByInquiryId(inquiryId, projectId);
    } catch (SQLException e) {
      logger.debug("Sql exception caught: " + e);
    }
  }

  /**
   * Creates a new tentative inquiry.
   * Tentative inquiries are those that are submitted by the central search tool. They will be
   * purged after a short period of time if no action by the user is taken.
   *
   * @param query  the query criteria
   * @param userid the id of the user that cretaed the inquiry
   * @return the id of the newly created tentative inquiry
   */
  int createTentative(Query query, int userid) {
    int returnValue = 0;
    UserDao userDao;
    User user;
    Inquiry inquiry;

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);

      userDao = new UserDao(configuration);
      user = userDao.fetchOneById(userid);
      if (user == null) {
        return 0;
      }

      inquiry = new Inquiry();
      inquiry.setAuthorId(user.getId());

      // Revision 0 = tentative
      inquiry.setRevision(0);

      inquiry.setStatus(InquiryStatus.IS_DRAFT);

      returnValue = saveTentativeInquiry(inquiry, connection).getValue(Tables.INQUIRY.ID);

      createAndSaveInquiryCriteriaTypeQuery(query, inquiry, connection);
    } catch (JAXBException e1) {
      e1.printStackTrace();
      return 0;
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return returnValue;
  }

  /**
   * Link a document with a project and/or inquiry.
   *
   * @param projectId        the id of the project to link the document with, if any
   * @param inquiryId        the id of the inquiry to link the document with
   * @param userId           the id of the user that uploaded the document
   * @param documentData     the document itself
   * @param documentFilename the name of the document
   * @param documentFiletype the filetype of the document
   * @param documentType     the type of the document (expose, vote, report...)
   * @return the id of the newly added document
   */
  int addDocument(Integer projectId, Integer inquiryId, int userId, File documentData,
      String documentFilename, String documentFiletype, DocumentType documentType) {
    int returnValue = 0;

    try (Connection connection = ResourceManager.getConnection()) {
      DSLContext dslContext = ResourceManager.getDslContext(connection);

      if (documentData != null) {
        Path path = documentData.toPath();
        Record documentRecord = dslContext.insertInto(Tables.DOCUMENT, Tables.DOCUMENT.DATA,
            Tables.DOCUMENT.FILENAME, Tables.DOCUMENT.FILETYPE, Tables.DOCUMENT.PROJECT_ID,
            Tables.DOCUMENT.INQUIRY_ID, Tables.DOCUMENT.USER_ID, Tables.DOCUMENT.DOCUMENT_TYPE)
            .values(Files.readAllBytes(path),
                documentFilename, documentFiletype, projectId,
                inquiryId, userId, documentType).returning(Tables.DOCUMENT.ID).fetchOne();

        returnValue = documentRecord.getValue(Tables.DOCUMENT.ID);
      }
    } catch (SQLException | IOException e) {
      e.printStackTrace();
    }

    return returnValue;
  }

  /**
   * Link the inquiry with the sites it will be sent to.
   *
   * @param inquiryId the id of the inquiry
   * @param siteIds   a list of the ids of the sites to send the inquiry to
   * @return true on success, false on error
   */
  public boolean setSitesForInquiry(int inquiryId, List<String> siteIds) {
    boolean ret = true;

    try (Connection connection = ResourceManager.getConnection()) {
      DSLContext dslContext = ResourceManager.getDslContext(connection);

      // Clear all sites first. In case this is a modification by the ccp office.

      dslContext.deleteFrom(Tables.INQUIRY_SITE)
          .where(Tables.INQUIRY_SITE.INQUIRY_ID.equal(inquiryId))
          .execute();

      for (String site : siteIds) {
        dslContext.insertInto(Tables.INQUIRY_SITE, Tables.INQUIRY_SITE.INQUIRY_ID,
            Tables.INQUIRY_SITE.SITE_ID)
            .values(inquiryId, Integer.parseInt(site)).execute();
      }

    } catch (SQLException e) {
      logger.error("Error adding sites for distribution for inquiry " + inquiryId);
      ret = false;
    }
    return ret;
  }

  // TODO: bankId parameter is currently basically useless - may change with access restrictions

  /**
   * List all (non-tentative) inquiries.
   * Tentative inquiries are identified by a revision nr of 0.
   *
   * @param bankId the id of the bank that wants to list the inquiries
   * @return the serialized list of inquiry ids
   */
  protected String list(int bankId) {
    List<Inquiry> inquiryList;
    InquiriesIdList inquiriesIdList = new InquiriesIdList();

    try {
      BankSite bankSite = BankSiteUtil.fetchBankSiteByBankId(bankId);
      if (bankSite == null) {
        logger.warn(
            "No Bank site for bank id '" + bankId + "' is found. Not providing any inquiries.");
        return writeXml(inquiriesIdList);
      }

      Site site = SiteUtil.fetchSiteById(bankSite.getSiteId());
      Integer siteIdForBank = site.getId();

      if (siteIdForBank == null || siteIdForBank < 1) {
        logger.warn(
            "Bank " + bankId + " is not associated with a site. Not providing any inquiries.");
        return writeXml(inquiriesIdList);
      }

      if (!bankSite.getApproved()) {
        logger.warn("Bank " + bankId
            + " is associated with a site, but that association has not been approved. "
            + "Not providing any inquiries.");
        return writeXml(inquiriesIdList);
      }

      inquiryList = InquiryUtil.fetchInquiriesForSite(siteIdForBank);

      for (Inquiry inquiry : inquiryList) {
        if ((inquiry.getRevision() < 1)) {
          continue;
        }

        InquiriesIdList.InquiryId inquiryXml = new InquiriesIdList.InquiryId();
        inquiriesIdList.getInquiryIds().add(inquiryXml);

        inquiryXml.setId(Integer.toString(inquiry.getId()));
        if (inquiry.getRevision() != null) {
          inquiryXml.setRevision(Integer.toString(inquiry.getRevision()));
        } else {
          inquiryXml.setRevision("1");
        }
      }
    } catch (NullPointerException npe) {
      logger.warn(
          "Nullpointer exception caught while trying to list inquiries. This might be caused by a"
              + " missing connection between bank and site. Check the DB. Returning empty list for"
              + " now.");
      inquiriesIdList.setInquiryIds(new ArrayList<>());

      return writeXml(inquiriesIdList);
    }

    return writeXml(inquiriesIdList);
  }

  private String writeXml(InquiriesIdList inquiries) {
    try {
      JAXBContext jaxbContext = JAXBContext.newInstance(InquiriesIdList.class);
      Marshaller marshaller = jaxbContext.createMarshaller();
      StringWriter writer = new StringWriter();
      marshaller.marshal(inquiries, writer);

      return writer.toString();
    } catch (JAXBException e) {
      logger.warn("JAXBException occured: " + e.getMessage());
      return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
          + "<Inquiries xmlns=\"http://schema.samply.de/common/Query\"></Inquiries>";
    }
  }

  /**
   * Gets an inquiry.
   *
   * @param inquiryId       the id of the inquiry to get
   * @param uriInfo         the uri info object linked with the request
   * @param userAgentHeader the user agent header of the requesting client
   * @param queryLanguage   either "CQL" or "QUERY"
   * @return the serialized inquiry
   */
  String getInquiry(int inquiryId, UriInfo uriInfo, String userAgentHeader, String queryLanguage) {
    StringBuilder returnValue = new StringBuilder();
    Inquiry inquiry;
    InquiryDao inquiryDao;
    User author;
    UserDao userDao;

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);

      inquiryDao = new InquiryDao(configuration);
      inquiry = inquiryDao.findById(inquiryId);

      if (inquiry == null) {
        return "notFound";
      }

      userDao = new UserDao(configuration);
      author = userDao.findById(inquiry.getAuthorId());

      if (author == null) {
        return "";
      }

      de.samply.share.model.common.Inquiry inq = new de.samply.share.model.common.Inquiry();

      UriBuilder uriBuilder = uriInfo.getBaseUriBuilder();
      URI uri = uriBuilder.build();
      inq.setExposeUrl(uri + "searchbroker/exposes/" + inquiryId);
      inq.setId(Integer.toString(inquiryId));
      if (inquiry.getRevision() != null && inquiry.getRevision() > 0) {
        inq.setRevision(Integer.toString(inquiry.getRevision()));
      } else {
        inq.setRevision("1");
      }

      inq.setLabel(inquiry.getLabel());
      inq.setDescription(inquiry.getDescription());

      if (isQueryLanguageViewQuery(queryLanguage)) {
        addCriteriaForViewQuery(inquiryId, inq);
      } else if (isQueryLanguageCql(queryLanguage)) {
        addCriteriaForCql(inquiryId, inq);
      } else {
        logger.warn(
            "Header parameter '" + Constants.HEADER_KEY_QUERY_LANGUAGE + "' has invalid value '"
                + queryLanguage + "'");
      }

      Contact authorInfo = getContact(author);
      inq.setAuthor(authorInfo);

      // Check if user agent of share client is at least 1.1.2. Otherwise skip result types
      String clientVersion = SamplyShareUtils.getVersionFromUserAgentHeader(userAgentHeader);
      if (userAgentHeader != null && clientVersion != null
          && SamplyShareUtils.versionCompare(clientVersion, "1.1.2") >= 0) {
        try {
          inq.getSearchFor().addAll(Splitter.on(',').splitToList(inquiry.getResultType()));
        } catch (NullPointerException npe) {
          logger.debug("No result type set...omitting in reply");
        }
      }

      StringWriter stringWriter = new StringWriter();
      JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
      Marshaller marshaller = jaxbContext.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

      ObjectFactory objectFactory = new ObjectFactory();

      marshaller.marshal(objectFactory.createInquiry(inq), stringWriter);

      returnValue.append(stringWriter.toString());

    } catch (SQLException e) {
      e.printStackTrace();
      returnValue.replace(0, returnValue.length(), "error");
    } catch (JAXBException e) {
      e.printStackTrace();
    }

    return returnValue.toString();
  }

  private void addCriteriaForViewQuery(int inquiryId, de.samply.share.model.common.Inquiry inq)
      throws JAXBException {
    String criteria = InquiryCriteriaUtil.fetchCriteriaForInquiryIdTypeQuery(inquiryId);
    StringReader stringReader = new StringReader(criteria);

    JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
    Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
    Query query = (Query) unmarshaller.unmarshal(stringReader);

    inq.setQuery(query);
  }

  private void addCriteriaForCql(int inquiryId, de.samply.share.model.common.Inquiry inq) {
    List<InquiryCriteria> criteriaList = InquiryCriteriaUtil
        .fetchCriteriaListForInquiryIdTypeCql(inquiryId);

    CqlQueryList cqlQueryList = new CqlQueryList();
    for (InquiryCriteria singleCriteria : criteriaList) {
      CqlQuery cqlQuery = new CqlQuery();
      cqlQuery.setEntityType(singleCriteria.getEntityType());
      cqlQuery.setCql(singleCriteria.getCriteria());

      cqlQueryList.getQueries().add(cqlQuery);
    }

    inq.setCqlQueryList(cqlQueryList);
  }

  /**
   * Gets the contact for a given user.
   *
   * @param user the user for which the contact is wanted
   * @return the contact of the user
   */
  private Contact getContact(User user) {
    de.samply.share.broker.model.db.tables.pojos.Contact contactPojo = ContactUtil
        .getContactForUser(user);
    Contact contact = null;

    if (contactPojo != null) {
      contact = new Contact();
      contact.setEmail(contactPojo.getEmail());
      contact.setFirstname(contactPojo.getFirstname());
      contact.setLastname(contactPojo.getLastname());
      contact.setPhone(contactPojo.getPhone());
      contact.setTitle(contactPojo.getTitle());
      contact.setOrganization(contactPojo.getOrganizationname());
    }

    return contact;
  }

  /**
   * Gets the contact for a given inquiry id.
   *
   * @param inquiryId the id of the inquiry for which the author will be returned
   * @return the contact of the author
   */
  String getContact(int inquiryId) throws JAXBException {
    de.samply.share.broker.model.db.tables.pojos.Contact contactPojo;
    Contact contact = new Contact();
    ContactDao contactDao;
    Inquiry inquiry;
    InquiryDao inquiryDao;
    User author;
    UserDao userDao;

    String ret = "";

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);

      inquiryDao = new InquiryDao(configuration);
      inquiry = inquiryDao.findById(inquiryId);

      if (inquiry == null) {
        logger.warn("Inquiry for requested contact not found. Inquiry id " + inquiryId
            + ". Returning empty contact...");
        contact.setEmail("-");
        contact.setFirstname("-");
        contact.setLastname("-");
        contact.setOrganization("-");
        contact.setPhone("-");
        contact.setTitle("-");
      } else {
        userDao = new UserDao(configuration);
        author = userDao.findById(inquiry.getAuthorId());

        contactDao = new ContactDao(configuration);
        contactPojo = contactDao.fetchOneById(author.getContactId());

        contact.setEmail(contactPojo.getEmail());
        contact.setFirstname(contactPojo.getFirstname());
        contact.setLastname(contactPojo.getLastname());
        contact.setOrganization(contactPojo.getOrganizationname());
        contact.setPhone(contactPojo.getPhone());
        contact.setTitle(contactPojo.getTitle());
      }

      JAXBContext jaxbContext = JAXBContext.newInstance(Contact.class);
      ObjectFactory objectFactory = new ObjectFactory();
      StringWriter stringWriter = new StringWriter();
      Marshaller marshaller = jaxbContext.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
      marshaller.marshal(objectFactory.createContact(contact), stringWriter);

      ret = stringWriter.toString();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return ret;
  }

  // TODO: bankId parameter is currently basically useless - may change with access restrictions

  /**
   * Gets the query for an inquiry.
   *
   * @param inquiryId the id of the inquiry
   * @return the serialized query
   */
  String getQuery(int inquiryId) {
    StringBuilder returnValue = new StringBuilder();
    Inquiry inquiry;
    InquiryDao inquiryDao;

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);

      inquiryDao = new InquiryDao(configuration);
      inquiry = inquiryDao.findById(inquiryId);

      if (inquiry == null) {
        return "notFound";
      }

      String criteria = InquiryCriteriaUtil.fetchCriteriaForInquiryIdTypeQuery(inquiryId);
      returnValue.append(criteria);
    } catch (SQLException e) {
      e.printStackTrace();
      returnValue.replace(0, returnValue.length(), "error");
    }
    return returnValue.toString();
  }

  /**
   * Gets the ViewFields for an inquiry.
   *
   * @param inquiryId the id of the inquiry
   * @return the viewfields
   */
  String getViewFields(int inquiryId) {
    StringBuilder returnValue = new StringBuilder();
    Inquiry inquiry;
    InquiryDao inquiryDao;

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);

      inquiryDao = new InquiryDao(configuration);
      inquiry = inquiryDao.findById(inquiryId);

      if (inquiry == null) {
        return "notFound";
      }
      if (inquiry.getViewfields() == null) {
        return null;
      }
      returnValue.append(inquiry.getViewfields());
    } catch (SQLException e) {
      e.printStackTrace();
      returnValue.replace(0, returnValue.length(), "error");
    }
    return returnValue.toString();
  }

  /**
   * Gets the description for a given inquiry id.
   *
   * @param inquiryId the id of the inquiry for which the description will be returned
   * @return the description
   */
  String getInfo(int inquiryId) throws JAXBException {
    Inquiry inquiry;
    InquiryDao inquiryDao;

    String ret = "";

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);

      inquiryDao = new InquiryDao(configuration);
      inquiry = inquiryDao.findById(inquiryId);

      Info info = new Info();

      if (inquiry == null) {
        logger.warn("Inquiry for requested info not found. Inquiry id " + inquiryId
            + ". Returning empty info...");

        info.setDescription("-");
        info.setLabel("-");
        info.setRevision("0");
      } else {
        info.setDescription(inquiry.getDescription());
        info.setLabel(inquiry.getLabel());
        info.setRevision(Integer.toString(inquiry.getRevision()));
      }

      JAXBContext jaxbContext = JAXBContext.newInstance(Info.class);
      ObjectFactory objectFactory = new ObjectFactory();
      StringWriter stringWriter = new StringWriter();
      Marshaller marshaller = jaxbContext.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
      marshaller.marshal(objectFactory.createInfo(info), stringWriter);

      ret = stringWriter.toString();

    } catch (SQLException e) {
      e.printStackTrace();
    }
    return ret;
  }

  private void createAndSaveInquiryCriteria(EssentialSimpleQueryDto essentialSimpleQueryDto,
      Inquiry inquiry, Connection connection) {
    createAndSaveInquiryCriteriaTypeCql(essentialSimpleQueryDto, inquiry, connection);
    createAndSaveInquiryCriteriaTypeQuery(essentialSimpleQueryDto, inquiry, connection);
  }

  private void createAndSaveInquiryCriteriaTypeCql(EssentialSimpleQueryDto essentialSimpleQueryDto,
      Inquiry inquiry, Connection connection) {
    createAndSaveInquiryCriteriaTypeCqlPatient(essentialSimpleQueryDto, inquiry, connection);
    createAndSaveInquiryCriteriaTypeCqlSpecimen(essentialSimpleQueryDto, inquiry, connection);
  }

  private void createAndSaveInquiryCriteriaTypeCql(String cql, Inquiry inquiry,
      Connection connection, String entityType) {
    InquiryCriteria inquiryCriteria = new InquiryCriteria();
    inquiryCriteria.setCriteria(cql);
    inquiryCriteria.setInquiryId(inquiry.getId());
    inquiryCriteria.setType(InquiryCriteriaType.IC_CQL);
    inquiryCriteria.setEntityType(entityType);

    saveInquiryCriteria(inquiryCriteria, connection);
  }

  private void createAndSaveInquiryCriteriaTypeCqlPatient(
      EssentialSimpleQueryDto essentialSimpleQueryDto, Inquiry inquiry, Connection connection) {
    String cql = createCqlPatient(essentialSimpleQueryDto);

    createAndSaveInquiryCriteriaTypeCql(cql, inquiry, connection, ENTITY_TYPE_FOR_CQL_PATIENT);
  }

  private void createAndSaveInquiryCriteriaTypeCqlSpecimen(
      EssentialSimpleQueryDto essentialSimpleQueryDto, Inquiry inquiry, Connection connection) {
    String cql = createCqlSpecimen(essentialSimpleQueryDto);

    createAndSaveInquiryCriteriaTypeCql(cql, inquiry, connection, ENTITY_TYPE_FOR_CQL_SPECIMEN);
  }

  private String createCqlPatient(EssentialSimpleQueryDto essentialSimpleQueryDto) {
    return createCql(essentialSimpleQueryDto, ENTITY_TYPE_FOR_CQL_PATIENT);
  }

  private String createCqlSpecimen(EssentialSimpleQueryDto essentialSimpleQueryDto) {
    return createCql(essentialSimpleQueryDto, ENTITY_TYPE_FOR_CQL_SPECIMEN);
  }

  private String createCql(EssentialSimpleQueryDto essentialSimpleQueryDto, String entityType) {
    return new EssentialSimpleQueryDto2CqlTransformer()
        .toQuery(essentialSimpleQueryDto, entityType);
  }

  private void createAndSaveInquiryCriteriaTypeQuery(
      EssentialSimpleQueryDto essentialSimpleQueryDto, Inquiry inquiry, Connection connection) {
    Query query;

    if (CollectionUtils.isEmpty(essentialSimpleQueryDto.getFieldDtos())) {
      query = new EssentialSimpleQueryDto2ShareXmlTransformer().toQuery(essentialSimpleQueryDto);
    } else {
      query = new Query();
      Where where = new Where();
      And and = new And();
      where.getAndOrEqOrLike().add(and);
      query.setWhere(where);
    }

    try {
      createAndSaveInquiryCriteriaTypeQuery(query, inquiry, connection);
    } catch (JAXBException e) {
      e.printStackTrace();
    }
  }

  private void createAndSaveInquiryCriteriaTypeQuery(Query query, Inquiry inquiry,
      Connection connection) throws JAXBException {
    JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
    StringWriter stringWriter = new StringWriter();
    Marshaller marshaller = jaxbContext.createMarshaller();
    marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
    marshaller.marshal(query, stringWriter);

    InquiryCriteria inquiryCriteria = new InquiryCriteria();
    inquiryCriteria.setCriteria(stringWriter.toString());
    inquiryCriteria.setInquiryId(inquiry.getId());
    inquiryCriteria.setType(InquiryCriteriaType.IC_QUERY);
    inquiryCriteria.setEntityType(ENTITY_TYPE_FOR_QUERY);

    saveInquiryCriteria(inquiryCriteria, connection);
  }

  private void saveInquiryCriteria(InquiryCriteria inquiryCriteria, Connection connection) {
    DSLContext dslContext = ResourceManager.getDslContext(connection);

    dslContext
        .insertInto(Tables.INQUIRY_CRITERIA,
            Tables.INQUIRY_CRITERIA.INQUIRY_ID,
            Tables.INQUIRY_CRITERIA.TYPE,
            Tables.INQUIRY_CRITERIA.CRITERIA,
            Tables.INQUIRY_CRITERIA.ENTITY_TYPE)
        .values(inquiryCriteria.getInquiryId(),
            inquiryCriteria.getType(),
            inquiryCriteria.getCriteria(),
            inquiryCriteria.getEntityType())
        .execute();
  }

  private Record saveInquiry(Inquiry inquiry, Connection connection) {
    DSLContext dslContext = ResourceManager.getDslContext(connection);

    return dslContext
        .insertInto(Tables.INQUIRY,
            Tables.INQUIRY.AUTHOR_ID,
            Tables.INQUIRY.STATUS,
            Tables.INQUIRY.REVISION,
            Tables.INQUIRY.LABEL,
            Tables.INQUIRY.DESCRIPTION,
            Tables.INQUIRY.RESULT_TYPE,
            Tables.INQUIRY.EXPIRES)
        .values(inquiry.getAuthorId(),
            inquiry.getStatus(),
            inquiry.getRevision(),
            inquiry.getLabel(),
            inquiry.getDescription(),
            inquiry.getResultType(),
            null)
        .returning(Tables.INQUIRY.ID, Tables.INQUIRY.CREATED)
        .fetchOne();
  }

  private Record saveTentativeInquiry(Inquiry inquiry, Connection connection) {
    DSLContext dslContext = ResourceManager.getDslContext(connection);

    return dslContext
        .insertInto(Tables.INQUIRY,
            Tables.INQUIRY.AUTHOR_ID,
            Tables.INQUIRY.STATUS,
            Tables.INQUIRY.REVISION)
        .values(inquiry.getAuthorId(),
            inquiry.getStatus(),
            inquiry.getRevision())
        .returning(Tables.INQUIRY.ID, Tables.INQUIRY.CREATED)
        .fetchOne();
  }

  /**
   * Save a reply to a given inquiry.
   *
   * @param inquiryId the id of the inquiry the reply is sent to
   * @param bankId    the id of the bank that sent the reply
   * @param content   the content of the reply
   * @param timestamp the timestamp of the result
   * @return true, if successful
   */
  boolean saveReply(int inquiryId, int bankId, String content, Timestamp timestamp) {
    boolean returnValue = true;
    Reply reply;
    ReplyDao replyDao;
    Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    de.samply.share.model.common.result.Reply replyJson = gson.fromJson(content,
        de.samply.share.model.common.result.Reply.class);
    replyJson.setSite(
        SiteUtil.fetchSiteById(BankSiteUtil.fetchBankSiteByBankId(bankId).getSiteId()).getName());
    content = gson.toJson(replyJson);

    try (Connection connection = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(connection)
          .set(SQLDialect.POSTGRES);
      DSLContext dslContext = ResourceManager.getDslContext(connection);
      replyDao = new ReplyDao(configuration);

      Record record = dslContext.select().from(Tables.REPLY)
          .where(Tables.REPLY.BANK_ID.equal(bankId).and(Tables.REPLY.INQUIRY_ID.equal(inquiryId)))
          .fetchAny();

      if (record == null) {
        reply = new Reply();
        reply.setBankId(bankId);
        reply.setInquiryId(inquiryId);
        reply.setContent(content);
        reply.setRetrievedat(timestamp);
        replyDao.insert(reply);
      } else {
        reply = replyDao.findById(record.getValue(Tables.REPLY.ID));
        reply.setContent(content);
        reply.setRetrievedat(timestamp);
        replyDao.update(reply);
      }

    } catch (SQLException e) {
      e.printStackTrace();
      returnValue = false;
    }

    return returnValue;
  }

  private boolean isEmpty(EssentialSimpleValueDto valueDto) {
    return isEmptyValue(valueDto.getValue())
        || (valueDto.getCondition() == SimpleValueCondition.BETWEEN && isEmptyValue(
            valueDto.getMaxValue()));
  }

  private boolean isEmpty(EssentialSimpleFieldDto fieldDto) {
    return CollectionUtils.isEmpty(fieldDto.getValueDtos());
  }

  private boolean isEmptyValue(String value) {
    return StringUtils.isEmpty(value) || "null".equalsIgnoreCase(value);
  }

}
