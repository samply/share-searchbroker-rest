package de.samply.share.broker.monitoring;

import de.samply.share.common.model.dto.monitoring.StatusReportItem;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

public class Report {

  private String target;
  private StatusReportItem statusReportItem;
  private double executionTime;

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public StatusReportItem getStatusReportItem() {
    return statusReportItem;
  }

  public void setStatusReportItem(
      StatusReportItem statusReportItem) {
    this.statusReportItem = statusReportItem;
  }

  public double getExecutionTime() {
    return executionTime;
  }

  public void setExecutionTime(double executionTime) {
    this.executionTime = executionTime;
  }

  public double calculateExecutionTime(Timestamp inquiryRetrievedAt,
      Timestamp resultRetrievedAt) {
    long diffInMS = resultRetrievedAt.getTime() - inquiryRetrievedAt.getTime();
    return TimeUnit.MILLISECONDS.toSeconds(diffInMS);
  }


}
