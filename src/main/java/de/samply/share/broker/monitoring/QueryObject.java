package de.samply.share.broker.monitoring;

import de.samply.share.model.cql.CqlQueryList;
import java.util.List;

public class QueryObject {

  private CqlQueryList cqlQueryList;
  private List<String> target;

  public CqlQueryList getCqlQueryList() {
    return cqlQueryList;
  }

  public void setCqlQueryList(CqlQueryList cqlQueryList) {
    this.cqlQueryList = cqlQueryList;
  }

  public List<String> getTarget() {
    return target;
  }

  public void setTarget(List<String> target) {
    this.target = target;
  }
}
