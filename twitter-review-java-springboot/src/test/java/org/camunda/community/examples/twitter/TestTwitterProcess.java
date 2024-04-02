package org.camunda.community.examples.twitter;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static io.camunda.zeebe.protocol.Protocol.USER_TASK_JOB_TYPE;
import static io.camunda.zeebe.spring.test.ZeebeTestThreadSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.assertions.ProcessInstanceAssert;
import io.camunda.zeebe.process.test.inspections.InspectionUtility;
import io.camunda.zeebe.process.test.inspections.model.InspectedProcessInstance;
import io.camunda.zeebe.spring.test.ZeebeSpringTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import org.camunda.community.examples.twitter.business.DuplicateTweetException;
import org.camunda.community.examples.twitter.business.ScheduleService;
import org.camunda.community.examples.twitter.business.TwitterService;
import org.camunda.community.examples.twitter.process.TwitterProcessVariables;
import org.camunda.community.examples.twitter.rest.ReviewTweetRestApi;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@ZeebeSpringTest
public class TestTwitterProcess {

  @Autowired private ZeebeClient zeebe;

  // TODO: We should probably get rid of this in Spring tests or at least hide it
  // somewhere
  // At the moment we have two different ways of waiting: Multi-threaded waiting,
  // and the "engine
  // run to completion"
  @Autowired private ZeebeTestEngine zeebeTestEngine;

  @MockBean private TwitterService twitterService;

  @MockBean private ScheduleService scheduleService;

  @Test
  public void test() {
    loadJson(
        "\\src\\main\\java\\org\\camunda\\community\\examples\\twitter\\business\\LoanDetails.json");
  }

  /**
   * Load JsonNode from file and parse into JsonNodes
   *
   * @author james.vicente
   * @param path jsonNode path
   * @return jn parse jsonNode
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> loadJson(String path) {
    String userDir = System.getProperty("user.dir");
    Path fileName = Path.of(userDir + path);
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = new HashMap<String, Object>();
    try {
      String str = Files.readString(fileName);
      map = mapper.readValue(str, HashMap.class);
      // System.out.println(ld.getId());
      // System.out.println(ld.getAccountHolderKey());
      // System.out.println(ld.getDisbursementDetails().get("encodedKey"));
      // System.out.println(ld.getTotalDue());
    } catch (IOException e) {
      e.printStackTrace();
    }
    return map;
  }

  @Test
  public void redrawCampaignPaymentSubprocess() throws Exception {
    Map<String, Object> variables =
        loadJson(
            "\\src\\main\\java\\org\\camunda\\community\\examples\\twitter\\business\\LoanDetails.json");

    // When
    DeploymentEvent deploymentEvent =
        zeebe
            .newDeployResourceCommand()
            .addResourceFromClasspath("Redraw Campaign Payment Subprocess v4.bpmn")
            .send()
            .join();

    // Then
    BpmnAssert.assertThat(deploymentEvent);

    // When
    ProcessInstanceEvent processInstance =
        zeebe
            .newCreateInstanceCommand()
            .bpmnProcessId("Redraw_Campaign_Payment_Subprocess")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // Then
    ProcessInstanceAssert processInstanceAssertions = BpmnAssert.assertThat(processInstance);
    processInstanceAssertions.hasPassedElement("StartEvent_1");
    waitForUserTaskAndComplete("GET_LOAN_DETAILS", "io.camunda:http-json:1", null);
    waitForUserTaskAndComplete("DETERMINE_REPAYMENT_STATUS", "status", variables);
    waitForUserTaskAndComplete("INITIATE_PAYMENT", "io.camunda:http-json:1", null);

    waitForProcessInstanceCompleted(processInstance.getProcessInstanceKey());

    // Let's assert that it passed certain BPMN elements (more to show off features
    // here)
    assertThat(processInstance)
        .hasPassedElement("Event_04juaw5")
        .hasNotPassedElement("endEvent_2")
        .isCompleted();

    Mockito.verify(scheduleService).getSchedule("test");
    Mockito.verifyNoMoreInteractions(scheduleService);
  }

  @Test
  public void approved() throws Exception {
    Map<String, Object> variables = new HashMap<String, Object>();
    variables.put("User", "James");

    // When
    DeploymentEvent deploymentEvent =
        zeebe
            .newDeployResourceCommand()
            .addResourceFromClasspath("mock_test_version6.bpmn")
            .send()
            .join();

    // Then
    BpmnAssert.assertThat(deploymentEvent);

    // When
    ProcessInstanceEvent processInstance =
        zeebe
            .newCreateInstanceCommand()
            .bpmnProcessId("Mock_Test_api")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // Then
    ProcessInstanceAssert processInstanceAssertions = BpmnAssert.assertThat(processInstance);
    processInstanceAssertions.hasPassedElement("startEvent_1");

    waitForUserTaskAndComplete("user_review", Collections.singletonMap("approved", true));

    waitForProcessInstanceCompleted(processInstance.getProcessInstanceKey());

    // Let's assert that it passed certain BPMN elements (more to show off features
    // here)
    assertThat(processInstance)
        .hasPassedElement("endEvent_1")
        .hasNotPassedElement("endEvent_2")
        .isCompleted();

    Mockito.verify(scheduleService).getSchedule("test");
    Mockito.verifyNoMoreInteractions(scheduleService);
  }

  @Test
  public void reject() throws Exception {

    Map<String, Object> variables = new HashMap<String, Object>();
    variables.put("User", "James");

    // When
    DeploymentEvent deploymentEvent =
        zeebe
            .newDeployResourceCommand()
            .addResourceFromClasspath("mock_test_version6.bpmn")
            .send()
            .join();

    // Then
    BpmnAssert.assertThat(deploymentEvent);

    // When
    ProcessInstanceEvent processInstance =
        zeebe
            .newCreateInstanceCommand()
            .bpmnProcessId("Mock_Test_api")
            .latestVersion()
            .variables(variables)
            .send()
            .join();

    // Then
    ProcessInstanceAssert processInstanceAssertions = BpmnAssert.assertThat(processInstance);
    processInstanceAssertions.hasPassedElement("startEvent_1");

    waitForUserTaskAndComplete("user_review", Collections.singletonMap("approved", false));

    waitForProcessInstanceCompleted(processInstance.getProcessInstanceKey());

    // Let's assert that it passed certain BPMN elements (more to show off features
    // here)
    assertThat(processInstance)
        .hasPassedElement("endEvent_2")
        .hasNotPassedElement("endEvent_1")
        .isCompleted();
    Mockito.verify(scheduleService, never()).getSchedule(anyString());
  }

  @Test
  public void testTweetApproved() throws Exception {
    // Prepare data input
    // TwitterProcessVariables variables =
    // new TwitterProcessVariables().setTweet("Hello world").setBoss("Zeebot");

    // When
    DeploymentEvent deploymentEvent =
        zeebe
            .newDeployResourceCommand()
            .addResourceFromClasspath("mock_test_withServiceTask.bpmn")
            .send()
            .join();

    // Then
    BpmnAssert.assertThat(deploymentEvent);

    // When
    ProcessInstanceEvent processInstance =
        zeebe
            .newCreateInstanceCommand()
            .bpmnProcessId("Mock_Test_api")
            .latestVersion()
            .send()
            .join();

    // Then
    ProcessInstanceAssert processInstanceAssertions = BpmnAssert.assertThat(processInstance);
    processInstanceAssertions.hasPassedElement("startEvent_1");

    // Now get all user tasks
    // List<ActivatedJob> jobs =
    // zeebe
    // .newActivateJobsCommand()
    // .jobType("io.camunda:http-json:1")
    // .maxJobsToActivate(1)
    // .send()
    // .join()
    // .getJobs();

    // When
    ActivateJobsResponse response =
        zeebe
            .newActivateJobsCommand()
            .jobType("io.camunda:http-json:1")
            .maxJobsToActivate(1)
            .send()
            .join();

    // // Then
    ActivatedJob activatedJob = response.getJobs().get(0);
    BpmnAssert.assertThat(activatedJob);

    // // TODO: invoke service task logic as required
    zeebe.newCompleteCommand(activatedJob.getKey()).send().join();

    BpmnAssert.assertThat(processInstance).isCompleted();

    // // start a process instance
    // ProcessInstanceEvent processInstance =
    // zeebe
    // .newCreateInstanceCommand() //
    // .bpmnProcessId("Mock_Test_api")
    // .latestVersion() //
    // // .variables(variables) //
    // .send()
    // .join();

    // // And then retrieve the UserTask and complete it with 'approved = true'
    // waitForUserTaskAndComplete("Activity_0h59oe5", null);

    // // Now the process should run to the end
    // waitForProcessInstanceCompleted(processInstance);

    // // Let's assert that it passed certain BPMN elements (more to show off
    // // features
    // // here)
    // assertThat(processInstance)
    // .hasPassedElement("end_event_tweet_published")
    // .hasNotPassedElement("end_event_tweet_rejected")
    // .isCompleted();

    // // And verify it caused the right side effects b calling the business methods
    Mockito.verify(twitterService).tweet("Hello world");
    Mockito.verifyNoMoreInteractions(twitterService);
  }

  @Test
  public void testRejectionPath() throws Exception {
    // When
    DeploymentEvent deploymentEvent =
        zeebe
            .newDeployResourceCommand()
            // .addProcessModel(process, "mock_test_version4.bpmn")
            .addResourceFromClasspath("twitter.bpmn")
            .send()
            .join();

    TwitterProcessVariables variables =
        new TwitterProcessVariables().setTweet("Hello world").setBoss("Zeebot");

    ProcessInstanceEvent processInstance =
        zeebe
            .newCreateInstanceCommand() //
            .bpmnProcessId("TwitterDemoProcess")
            .latestVersion() //
            .variables(variables) //
            .send()
            .join();

    waitForUserTaskAndComplete(
        "user_task_review_tweet", Collections.singletonMap("approved", false));

    waitForProcessInstanceCompleted(processInstance);
    waitForProcessInstanceHasPassedElement(processInstance, "end_event_tweet_rejected");
    Mockito.verify(twitterService, never()).tweet(anyString());
  }

  @Test
  public void testDuplicateTweet() throws Exception {
    // throw exception simulating duplicateM
    Mockito.doThrow(new DuplicateTweetException("DUPLICATE"))
        .when(twitterService)
        .tweet(anyString());

    // When
    DeploymentEvent deploymentEvent =
        zeebe
            .newDeployResourceCommand()
            // .addProcessModel(process, "mock_test_version4.bpmn")
            .addResourceFromClasspath("twitter.bpmn")
            .send()
            .join();

    TwitterProcessVariables variables =
        new TwitterProcessVariables().setTweet("Hello world").setAuthor("bernd").setBoss("Zeebot");

    ProcessInstanceEvent processInstance =
        zeebe
            .newCreateInstanceCommand() //
            .bpmnProcessId("TwitterDemoProcess")
            .latestVersion() //
            .variables(variables) //
            .send()
            .join();

    waitForUserTaskAndComplete(
        "user_task_review_tweet", Collections.singletonMap("approved", true));
    waitForProcessInstanceHasPassedElement(processInstance, "boundary_event_tweet_duplicated");
    waitForUserTaskAndComplete("user_task_handle_duplicate", new HashMap<>());
    // second try :-) --> TODO: Think about isolation of test cases when we can
    // better cleanup the
    // engine

    Mockito.doNothing().when(twitterService).tweet(anyString());
    waitForUserTaskAndComplete(
        "user_task_review_tweet", Collections.singletonMap("approved", true));
    waitForProcessInstanceCompleted(processInstance);
  }

  public void waitForUserTaskAndComplete(String userTaskId, Map<String, Object> variables)
      throws InterruptedException, TimeoutException {
    // Let the workflow engine do whatever it needs to do
    zeebeTestEngine.waitForIdleState(Duration.ofMinutes(5));

    // Now get all user tasks
    List<ActivatedJob> jobs =
        zeebe
            .newActivateJobsCommand()
            .jobType(USER_TASK_JOB_TYPE)
            .maxJobsToActivate(1)
            .workerName("waitForUserTaskAndComplete")
            .send()
            .join()
            .getJobs();

    // Should be only one
    assertTrue(jobs.size() > 0, "Job for user task '" + userTaskId + "' does not exist");
    ActivatedJob userTaskJob = jobs.get(0);
    // Make sure it is the right one
    if (userTaskId != null) {
      assertEquals(userTaskId, userTaskJob.getElementId());
    }

    // And complete it passing the variables
    if (variables != null && variables.size() > 0) {
      zeebe.newCompleteCommand(userTaskJob.getKey()).variables(variables).send().join();
    } else {
      zeebe.newCompleteCommand(userTaskJob.getKey()).send().join();
    }
  }

  public void waitForUserTaskAndComplete(
      String userTaskId, String type, Map<String, Object> variables)
      throws InterruptedException, TimeoutException {
    // Let the workflow engine do whatever it needs to do
    zeebeTestEngine.waitForIdleState(Duration.ofMinutes(5));

    // Now get all user tasks
    List<ActivatedJob> jobs =
        zeebe
            .newActivateJobsCommand()
            .jobType(type)
            .maxJobsToActivate(1)
            .workerName("waitForUserTaskAndComplete")
            .send()
            .join()
            .getJobs();

    // Should be only one
    assertTrue(jobs.size() > 0, "Job for user task '" + userTaskId + "' does not exist");
    ActivatedJob userTaskJob = jobs.get(0);
    // Make sure it is the right one
    if (userTaskId != null) {
      assertEquals(userTaskId, userTaskJob.getElementId());
    }

    // And complete it passing the variables
    if (variables != null && variables.size() > 0) {
      zeebe.newCompleteCommand(userTaskJob.getKey()).variables(variables).send().join();
    } else {
      zeebe.newCompleteCommand(userTaskJob.getKey()).send().join();
    }
  }

  @Autowired private ReviewTweetRestApi restApi;

  /**
   * This is an alternative test that uses the REST API code instead of directly starting a process
   * instance This is even more realistic, as it also validates the data input mapping
   */
  @Test
  public void testTweetApprovedByRestApi() throws Exception {

    // When
    DeploymentEvent deploymentEvent =
        zeebe
            .newDeployResourceCommand()
            // .addProcessModel(process, "mock_test_version4.bpmn")
            .addResourceFromClasspath("twitter.bpmn")
            .send()
            .join();
    restApi.startTweetReviewProcess("bernd", "Hello REST world", "Zeebot");
    zeebeTestEngine.waitForIdleState(Duration.ofMinutes(5));
    InspectedProcessInstance processInstance =
        InspectionUtility.findProcessInstances().findLastProcessInstance().get();

    // Let the workflow engine do whatever it needs to do
    // And then retrieve the UserTask and complete it with 'approved = true'
    waitForUserTaskAndComplete(
        "user_task_review_tweet", Collections.singletonMap("approved", true));

    waitForProcessInstanceCompleted(processInstance.getProcessInstanceKey());

    // Let's assaert that it passed certain BPMN elements (more to show off features
    // here)
    assertThat(processInstance)
        .hasPassedElement("end_event_tweet_published")
        .hasNotPassedElement("end_event_tweet_rejected")
        .isCompleted();

    // And verify it caused the right side effects b calling the business methods
    Mockito.verify(twitterService).tweet("Hello REST world");
    Mockito.verifyNoMoreInteractions(twitterService);
  }
}
