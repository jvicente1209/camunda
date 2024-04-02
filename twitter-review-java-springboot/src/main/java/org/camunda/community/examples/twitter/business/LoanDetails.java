package org.camunda.community.examples.twitter.business;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LoanDetails {

  String id;
  String encodedKey;
  String accountHolderKey;
  String totalDue;
  Map<String, Object> disbursementDetails;

  public Map<String, Object> getDisbursementDetails() {
    return disbursementDetails;
  }

  public void setDisbursementDetails(Map<String, Object> disbursementDetails) {
    this.disbursementDetails = disbursementDetails;
  }

  public String getEncodedKey() {
    return encodedKey;
  }

  public void setEncodedKey(String encodedKey) {
    this.encodedKey = encodedKey;
  }

  public String getAccountHolderKey() {
    return accountHolderKey;
  }

  public void setAccountHolderKey(String accountHolderKey) {
    this.accountHolderKey = accountHolderKey;
  }

  public String getTotalDue() {
    return totalDue;
  }

  public void setTotalDue(String totalDue) {
    this.totalDue = totalDue;
  }

  public String getId() {
    return id;
  }

  public void setid(String id) {
    this.id = id;
  }
}
