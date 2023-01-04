package uk.nhs.prm.repo.ehrtransferservice.parsers;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import uk.nhs.prm.repo.ehrtransferservice.LocalStackAwsConfig;
import uk.nhs.prm.repo.ehrtransferservice.activemq.ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient;
import uk.nhs.prm.repo.ehrtransferservice.activemq.SimpleAmqpQueue;
import uk.nhs.prm.repo.ehrtransferservice.utils.TestDataLoader;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient.class)
@SpringBootTest()
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LocalStackAwsConfig.class)
public class ParserBrokerIntegrationTest {
    @Autowired
    private AmazonSQSAsync sqs;

    @Value("${activemq.inboundQueue}")
    private String inboundQueue;

    @Value("${aws.largeMessageFragmentsObservabilityQueueName}")
    private String largeMessageFragmentsObservabilityQueueName;

    @Value("${aws.smallEhrObservabilityQueueName}")
    private String smallEhrObservabilityQueueName;

    @Value("${aws.largeEhrQueueName}")
    private String largeEhrQueueName;

    @Value("${aws.parsingDlqQueueName}")
    private String parsingDlqQueueName;

    @Value("${aws.ehrCompleteQueueName}")
    private String ehrCompleteQueueName;

    @Value("${aws.ehrInUnhandledObservabilityQueueName}")
    private String ehrInUnhandledObservabilityQueueName;

    private final TestDataLoader dataLoader = new TestDataLoader();

    @AfterEach
    public void tearDown() {
        purgeQueue(largeMessageFragmentsObservabilityQueueName);
        purgeQueue(smallEhrObservabilityQueueName);
        purgeQueue(largeEhrQueueName);
        purgeQueue(parsingDlqQueueName);
        purgeQueue(ehrCompleteQueueName);
        purgeQueue(ehrInUnhandledObservabilityQueueName);
    }

    @Test
    @Disabled
    void shouldPublishEhrRequestMessageToEhrInUnhandledObservabilityQueue() throws IOException {
        // get EHR Request test data as a string
        var ehrRequestMessageBody = dataLoader.getDataAsString("RCMR_IN010000UK05");

        // put that fake EHR Request on the inbound queue
        var inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendMessage(ehrRequestMessageBody);

        // get the queue url for ehr in unhandled observability queue
        var ehrInUnhandledObservabilityQueueUrl = sqs.getQueueUrl(ehrInUnhandledObservabilityQueueName).getQueueUrl();
        System.out.println("ehrInUnhandledObservabilityQueueUrl: " + ehrInUnhandledObservabilityQueueUrl);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            // get the messages from the queue
            var receivedMessageHolder = checkMessageInRelatedQueue(ehrInUnhandledObservabilityQueueUrl);

            // assert that the message content is as expected
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(ehrRequestMessageBody));
        });
    }

    @Test
    void shouldPublishCopcMessageToLargeMessageFragmentTopic() throws IOException {
        var attachmentMessageBody = dataLoader.getDataAsString("COPC_IN000001UK01");

        var inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendMessage(attachmentMessageBody);

        var attachmentsQueueUrl = sqs.getQueueUrl(largeMessageFragmentsObservabilityQueueName).getQueueUrl();
        System.out.println("attachmentsQueueUrl: " + attachmentsQueueUrl);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(attachmentsQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(attachmentMessageBody));
            Assertions.assertTrue(receivedMessageHolder.get(0).getMessageAttributes().containsKey("traceId"));
            Assertions.assertTrue(receivedMessageHolder.get(0).getMessageAttributes().containsKey("conversationId"));
        });
    }

    @Test
    void shouldPublishSmallMessageToSmallEhrObservabilityQueue() throws IOException {
        var smallEhrMessageBody = dataLoader.getDataAsString("RCMR_IN030000UK06");

        var inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendMessage(smallEhrMessageBody);

        var smallEhrObservabilityQueueUrl = sqs.getQueueUrl(smallEhrObservabilityQueueName).getQueueUrl();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(smallEhrObservabilityQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(smallEhrMessageBody));
            Assertions.assertTrue(receivedMessageHolder.get(0).getMessageAttributes().containsKey("traceId"));
            Assertions.assertTrue(receivedMessageHolder.get(0).getMessageAttributes().containsKey("conversationId"));
        });
    }

    @Test
    void shouldPassCorrelationIdToBeSetAsTraceId() throws IOException {
        var correlationId = UUID.randomUUID().toString();
        var smallEhrMessageBody = dataLoader.getDataAsString("RCMR_IN030000UK06");

        var inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendMessage(smallEhrMessageBody, correlationId);

        var smallEhrObservabilityQueueUrl = sqs.getQueueUrl(smallEhrObservabilityQueueName).getQueueUrl();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(smallEhrObservabilityQueueUrl);
            var message = receivedMessageHolder.get(0);
            Assertions.assertTrue(message.getBody().contains(smallEhrMessageBody));
            Assertions.assertTrue(message.getMessageAttributes().containsKey("traceId"));
            Assertions.assertEquals(message.getMessageAttributes().get("traceId").getStringValue(), correlationId);
        });
    }

    @Test
    void shouldPublishInvalidMessageToDlq() {
        var wrongMessage = "something wrong";

        var inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendMessage(wrongMessage);

        var parsingDqlQueueUrl = sqs.getQueueUrl(parsingDlqQueueName).getQueueUrl();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(parsingDqlQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(wrongMessage));
        });
    }

    @Test
    void shouldPublishUnprocessableMessageToDlq() {
        var unprocessableMessage = "NO_ACTION:UNPROCESSABLE_MESSAGE_BODY";
        var inboundQueueFromMhs = new SimpleAmqpQueue(inboundQueue);
        inboundQueueFromMhs.sendUnprocessableAmqpMessage();

        var parsingDqlQueueUrl = sqs.getQueueUrl(parsingDlqQueueName).getQueueUrl();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            var receivedMessageHolder = checkMessageInRelatedQueue(parsingDqlQueueUrl);
            Assertions.assertTrue(receivedMessageHolder.get(0).getBody().contains(unprocessableMessage));
        });
    }

    private List<Message> checkMessageInRelatedQueue(String queueUrl) {
        System.out.println("checking sqs queue: " + queueUrl);

        var requestForMessagesWithAttributes
                = new ReceiveMessageRequest().withQueueUrl(queueUrl)
                .withMessageAttributeNames("All");
        var messages = sqs.receiveMessage(requestForMessagesWithAttributes).getMessages();
        System.out.println("messages in checkMessageInRelatedQueue: " + messages);
        assertThat(messages).hasSize(1);
        return messages;
    }

    private void purgeQueue(String queueName) {
        var queueUrl = sqs.getQueueUrl(queueName).getQueueUrl();
        sqs.purgeQueue(new PurgeQueueRequest(queueUrl));
    }
}