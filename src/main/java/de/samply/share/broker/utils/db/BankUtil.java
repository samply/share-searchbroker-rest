package de.samply.share.broker.utils.db;

import de.samply.share.broker.jdbc.ResourceManager;
import de.samply.share.broker.model.db.Tables;
import de.samply.share.broker.model.db.tables.daos.BankDao;
import de.samply.share.broker.model.db.tables.pojos.Bank;
import de.samply.share.broker.model.db.tables.pojos.Site;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;

/**
 * This class provides static methods for CRUD operations for Bank Objects.
 *
 * @see Bank
 */
public final class BankUtil {

  private static final Logger logger = LogManager.getLogger(BankUtil.class);

  // Prevent instantiation
  private BankUtil() {
  }

  /**
   * Get the site for a bank.
   *
   * @param bank the bank for which to get the site
   * @return the site the bank is assigned to
   */
  public static Site getSiteForBank(Bank bank) {
    Site site = null;

    try (Connection conn = ResourceManager.getConnection()) {
      DSLContext dslContext = ResourceManager.getDslContext(conn);

      site = dslContext.select()
          .from(Tables.BANK.join(Tables.BANK_SITE).onKey().join(Tables.SITE).onKey())
          .where(Tables.BANK.ID.equal(bank.getId()))
          .fetchOneInto(Site.class);

    } catch (SQLException e) {
      logger.error("SQL Exception caught", e);
    }
    return site;
  }

  /**
   * Get the site for a bank.
   *
   * @param bankId the id of the bank for which to get the site
   * @return the site the bank is assigned to
   */
  public static Site getSiteForBankId(int bankId) {
    Site site;

    try (Connection conn = ResourceManager.getConnection()) {
      DSLContext dslContext = ResourceManager.getDslContext(conn);

      site = dslContext.select()
          .from(Tables.BANK.join(Tables.BANK_SITE).onKey().join(Tables.SITE).onKey())
          .where(Tables.BANK.ID.equal(bankId))
          .fetchOneInto(Site.class);

      return site;

    } catch (SQLException e) {
      logger.error("SQL Exception caught", e);
    }
    return null;
  }

  /**
   * Get the site id for a bank.
   *
   * @param bankId the id of the bank for which to get the site
   * @return the id of the site the bank is assigned to
   */
  public static Integer getSiteIdForBankId(int bankId) {
    Site site = getSiteForBankId(bankId);
    if (site == null) {
      return null;
    } else {
      return site.getId();
    }
  }

  /**
   * Get all banks.
   *
   * @return a list with all banks
   */
  public static List<Bank> fetchSites() {
    List<Bank> banks = null;
    BankDao bankDao;

    try (Connection conn = ResourceManager.getConnection()) {
      Configuration configuration = new DefaultConfiguration().set(conn).set(SQLDialect.POSTGRES);
      bankDao = new BankDao(configuration);
      banks = bankDao.findAll();
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return banks;
  }

}
