package de.samply.share.broker.monitoring;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class ResultList {

  private List<Report> reportList = new ArrayList<>();
  @JsonProperty("exit_status")
  @SerializedName("exit_status")
  private String exitStatus;

  public List<Report> getResultList() {
    return reportList;
  }

  public void setResultList(List<Report> reportList) {
    this.reportList = reportList;
  }

  public String getExitStatus() {
    return exitStatus;
  }

  public void setExitStatus(String exitStatus) {
    this.exitStatus = exitStatus;
  }
}
