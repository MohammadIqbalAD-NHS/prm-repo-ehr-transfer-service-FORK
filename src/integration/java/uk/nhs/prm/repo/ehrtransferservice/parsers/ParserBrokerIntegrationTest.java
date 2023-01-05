package uk.nhs.prm.repo.ehrtransferservice.parsers;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import uk.nhs.prm.repo.ehrtransferservice.LocalStackAwsConfig;
import uk.nhs.prm.repo.ehrtransferservice.activemq.ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient;
import uk.nhs.prm.repo.ehrtransferservice.activemq.SimpleAmqpQueue;
import uk.nhs.prm.repo.ehrtransferservice.database.TransferTrackerService;
import uk.nhs.prm.repo.ehrtransferservice.repo_incoming.RepoIncomingEvent;
import uk.nhs.prm.repo.ehrtransferservice.utils.TestDataLoader;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private DynamoDbClient dbClient;

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

    @Value("${aws.transferTrackerDbTableName}")
    private String transferTrackerDbTableName;

    private final TestDataLoader dataLoader = new TestDataLoader();

    @Autowired
    private TransferTrackerService transferTrackerService;

    private final String conversationIdForSmallEhr="ff27abc3-9730-40f7-ba82-382152e6b90a";
    private final String conversationIdForCopc="ff1457fb-4f58-4870-8d90-24d9c3ef8b91";

    @BeforeEach
    public void setup(){
        RepoIncomingEvent repoIncomingEventForSmallEhr= new RepoIncomingEvent("NHS_number_12312","gp_4823","NemsId_48309","dest_gp_2484","2023-01-05",conversationIdForSmallEhr);
        RepoIncomingEvent repoIncomingEventForCopc= new RepoIncomingEvent("NHS_number_12312","gp_4823","NemsId_48309","dest_gp_2484","2023-01-05",conversationIdForCopc);
        transferTrackerService.createEhrTransfer(repoIncomingEventForSmallEhr,"ACTION:EHR_REQUEST_SENT");
        transferTrackerService.createEhrTransfer(repoIncomingEventForCopc,"ACTION:EHR_REQUEST_SENT");
    }

    @AfterEach
    public void tearDown() {
        purgeQueue(largeMessageFragmentsObservabilityQueueName);
        purgeQueue(smallEhrObservabilityQueueName);
        purgeQueue(largeEhrQueueName);
        purgeQueue(parsingDlqQueueName);
        purgeQueue(ehrCompleteQueueName);
        purgeQueue(ehrInUnhandledObservabilityQueueName);


        Map<String, AttributeValue> key = new HashMap<>();
        key.put("conversation_id", AttributeValue.builder().s(conversationIdForSmallEhr).build());
        dbClient.deleteItem(DeleteItemRequest.builder().tableName(transferTrackerDbTableName).key(key).build());
        key.clear();
        key.put("conversation_id", AttributeValue.builder().s(conversationIdForCopc).build());
        dbClient.deleteItem(DeleteItemRequest.builder().tableName(transferTrackerDbTableName).key(key).build());
    }

    @Test
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