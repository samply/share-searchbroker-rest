package de.samply.share.broker.utils.db;

import de.samply.share.broker.jdbc.ResourceManager;
import de.samply.share.broker.model.db.tables.daos.BankDao;
import de.samply.share.broker.model.db.tables.daos.BankSiteDao;
import de.samply.share.broker.model.db.tables.daos.SiteDao;
import de.samply.share.broker.model.db.tables.pojos.Bank;
import de.samply.share.broker.model.db.tables.pojos.BankSite;
import de.samply.share.broker.model.db.tables.pojos.Site;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;

/**
 * This class provides static methods for CRUD operations for BankSite Objects.
 *
 * @see BankSite
 */
public final class BankSiteUtil {

  private static final Logger logger = LogManager.getLogger(BankSiteUtil.class);

  // Prevent instantiation
  private BankSiteUtil() {
  }

  /**
   * Update a bank-site relation.
   *
   * @param bankSite the bank-site relation to update
   */
  public static void updateBankSite(BankSite bankSite) {
    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);

      BankSiteDao bankSiteDao = new BankSiteDao(configuration);
      bankSiteDao.update(bankSite);
    } catch (SQLException e) {
      logger.error("SQL Exception caught", e);
    }
  }

  /**
   * Assign a bank to a site.
   *
   * @param bank     the bank to assign
   * @param site     the site to assign the bank to
   * @param approved has the assignment been checked and assured that it is correct?
   */
  public static void setSiteForBank(Bank bank, Site site, boolean approved) {
    BankSite newBankSite = null;
    BankSite oldBankSite = null;
    BankSiteDao bankSiteDao;

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);

      bankSiteDao = new BankSiteDao(configuration);
      List<BankSite> bankSites = bankSiteDao.fetchByBankId(bank.getId());

      if (bankSites != null && !bankSites.isEmpty()) {
        oldBankSite = bankSites.get(0);
        bankSiteDao.delete(oldBankSite);
      }

      newBankSite = new BankSite();
      newBankSite.setApproved(approved);
      newBankSite.setSiteId(site.getId());
      newBankSite.setBankId(bank.getId());
      bankSiteDao.insert(newBankSite);
    } catch (SQLException e) {
      logger.error("SQL Exception caught", e);
    }
  }


  /**
   * Assign a bank to a site.
   *
   * @param bank     the bank to assign
   * @param siteId   the id of the site to assign the bank to
   * @param approved has the assignment been checked and assured that it is correct?
   */
  private static void setSiteIdForBank(Bank bank, int siteId, boolean approved) {
    Site site;
    SiteDao siteDao;

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);

      siteDao = new SiteDao(configuration);
      site = siteDao.fetchOneById(siteId);
      if (site == null) {
        throw new RuntimeException("Site not found");
      } else {
        setSiteForBank(bank, site, approved);
      }

    } catch (SQLException e) {
      logger.error("SQL Exception caught", e);
    }
  }

  /**
   * Assign a bank to a site.
   *
   * @param bankId   the id of the bank to assign
   * @param siteId   the id of the site to assign the bank to
   * @param approved has the assignment been checked and assured that it is correct?
   */
  public static void setSiteIdForBankId(int bankId, int siteId, boolean approved) {
    Bank bank;
    BankDao bankDao;

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);

      bankDao = new BankDao(configuration);
      bank = bankDao.fetchOneById(bankId);
      if (bank == null) {
        throw new RuntimeException("Bank not found");
      } else {
        setSiteIdForBank(bank, siteId, approved);
      }

    } catch (SQLException e) {
      logger.error("SQL Exception caught", e);
    }
  }

  /**
   * Get the assignment between bank and site for a given bank.
   *
   * @param bank the bank for which to get the assignment
   * @return the assignment between bank and site
   */
  private static BankSite fetchBankSiteByBank(Bank bank) {
    List<BankSite> bankSites;
    BankSiteDao bankSiteDao;

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);
      bankSiteDao = new BankSiteDao(configuration);

      bankSites = bankSiteDao.fetchByBankId(bank.getId());
      // There shall be but one assignment
      if (bankSites != null && bankSites.size() == 1) {
        return bankSites.get(0);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Get the assignment between bank and site for a given bank id.
   *
   * @param bankId the id of the bank for which to get the assignment
   * @return the assignment between bank and site
   */
  public static BankSite fetchBankSiteByBankId(int bankId) {
    List<BankSite> bankSites;
    BankSiteDao bankSiteDao;

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);
      bankSiteDao = new BankSiteDao(configuration);

      bankSites = bankSiteDao.fetchByBankId(bankId);
      // There shall be but one assignment
      if (bankSites != null && bankSites.size() == 1) {
        return bankSites.get(0);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Delete a bank to site assignment.
   *
   * @param bankSite the bank to site assignment to delete
   */
  private static void deleteBankSite(BankSite bankSite) {
    BankSiteDao bankSiteDao;

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);
      bankSiteDao = new BankSiteDao(configuration);

      bankSiteDao.delete(bankSite);
    } catch (SQLException e) {
      logger.error("SQL Exception caught", e);
    }
  }

  /**
   * Get a bank with the searching site.
   * @param siteId the site id
   * @return the bank site of the matching site id
   */
  public static BankSite fetchBankSiteBySiteId(Integer siteId) {
    BankSiteDao bankSiteDao;
    List<BankSite> bankSites;

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);
      bankSiteDao = new BankSiteDao(configuration);

      bankSites = bankSiteDao.fetchBySiteId(siteId);
      // There shall be but one assignment
      if (bankSites != null && bankSites.size() == 1) {
        return bankSites.get(0);
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Get a list of bank with the searching sites.
   *
   * @param siteIdList the site ids
   * @return the bank sites of the matching site ids
   */
  public static List<BankSite> fetchBankSiteBySiteIdList(List<Integer> siteIdList) {
    BankSiteDao bankSiteDao;
    List<BankSite> bankSiteList = new ArrayList<>();

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);
      bankSiteDao = new BankSiteDao(configuration);
      for (int siteId : siteIdList) {
        List<BankSite> bankSite = bankSiteDao.fetchBySiteId(siteId);
        if (bankSite != null && bankSite.size() == 1) {
          bankSiteList.add(bankSite.get(0));
        }
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Delete the site assignment of a bank.
   *
   * @param bank the bank for which to clear the assignment to a site
   */
  public static void deleteSiteFromBank(Bank bank) {
    BankSite bankSite = fetchBankSiteByBank(bank);
    if (bankSite != null) {
      deleteBankSite(bankSite);
    }
  }
}
