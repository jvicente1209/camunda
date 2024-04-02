package org.camunda.community.examples.twitter.process;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import io.camunda.zeebe.spring.client.annotation.VariablesAsType;
import io.camunda.zeebe.spring.client.exception.ZeebeBpmnError;
import org.camunda.community.examples.twitter.business.DuplicateTweetException;
import org.camunda.community.examples.twitter.business.ScheduleService;
import org.camunda.community.examples.twitter.business.TwitterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TwitterWorker {

  @Autowired private TwitterService twitterService;
  @Autowired private ScheduleService scheduleService;

  @JobWorker(type = "publish-tweet")
  public void handleTweet(@VariablesAsType TwitterProcessVariables variables) throws Exception {
    try {
      twitterService.tweet(variables.getTweet());
    } catch (DuplicateTweetException ex) {
      throw new ZeebeBpmnError("duplicateMessage", "Could not post tweet, it is a duplicate.");
    }
  }

  @JobWorker(type = "send-rejection")
  public void sendRejection(@VariablesAsType TwitterProcessVariables variables) throws Exception {
    // same thing as above, do data transformation and delegate to real business code / service
  }

  @JobWorker(type = "statussss")
  public void getSchedule(ActivatedJob job) throws Exception {
    System.out.println("VARIABLES" + job.getVariables());
  }

  // @JobWorker(type = "io.camunda:http-json:1")
  public void getScheduleRest(@VariablesAsType TwitterProcessVariables variables) throws Exception {
    // scheduleService.getSchedule("test");
    System.out.println("TEST APPROVED REST");
  }

  @JobWorker(name = "Determine repayment status")
  public void sum(@VariablesAsType TwitterProcessVariables variables) throws Exception {
    System.out.println("TEST APPROVED REST");
  }
}
