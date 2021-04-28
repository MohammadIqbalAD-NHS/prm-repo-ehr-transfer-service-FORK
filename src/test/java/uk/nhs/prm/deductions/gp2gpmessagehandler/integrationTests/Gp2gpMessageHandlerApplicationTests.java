package uk.nhs.prm.deductions.gp2gpmessagehandler.integrationTests;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import de.mkammerer.wiremock.WireMockExtension;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import uk.nhs.prm.deductions.gp2gpmessagehandler.utils.TestDataLoader;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("integration") // perhaps we need other name for tests that interact with external systems
@SpringBootTest
class Gp2gpMessageHandlerApplicationTests {
    @RegisterExtension
    WireMockExtension wireMock = new WireMockExtension();

    @Autowired
    JmsTemplate jmsTemplate;

    @Value("${activemq.inboundQueue}")
    private String inboundQueue;

    @Value("${activemq.unhandledQueue}")
    private String unhandledQueue;

    private TestDataLoader dataLoader = new TestDataLoader();

    @Test
    void shouldUploadSmallEhrExtractToEhrRepoStorage() throws IOException, InterruptedException {
        String smallEhrExtract = dataLoader.getDataAsString("RCMR_IN030000UK06.xml");
        String url = String.format("%s/ehr-storage", wireMock.baseUrl());
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withBody(url).withStatus(200)));
        wireMock.stubFor(put(urlMatching("/ehr-storage")).willReturn(aResponse().withStatus(200)));
        wireMock.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(201)));
        wireMock.stubFor(patch(anyUrl()).willReturn(aResponse().withStatus(204)));

        jmsTemplate.send(inboundQueue, new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                BytesMessage bytesMessage = session.createBytesMessage();
                bytesMessage.writeBytes(smallEhrExtract.getBytes(StandardCharsets.UTF_8));
                return bytesMessage;
            }
        });
        sleep(5000);
        verify(getRequestedFor(urlMatching("/messages/5a36471b-036b-48e1-bbb4-a89aee0652e1/31fa3430-6e88-11ea-9384-e83935108fd5")));
        verify(putRequestedFor(urlMatching("/ehr-storage")).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalTo(smallEhrExtract)));
        verify(postRequestedFor(urlMatching("/messages")));
        verify(patchRequestedFor(urlMatching("/deduction-requests/5a36471b-036b-48e1-bbb4-a89aee0652e1/ehr-message-received")));
        jmsTemplate.setReceiveTimeout(1000);
        assertNull(jmsTemplate.receive(unhandledQueue));
    }

    @Test
    void shouldUploadLargeEhrExtractToEhrRepoStorage() throws IOException, InterruptedException {
        String largeEhrExtract = dataLoader.getDataAsString("ehrOneLargeMessage.xml");
        String url = String.format("%s/ehr-storage", wireMock.baseUrl());
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withBody(url).withStatus(200)));
        wireMock.stubFor(put(urlMatching("/ehr-storage")).willReturn(aResponse().withStatus(200)));
        wireMock.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(201)));
        wireMock.stubFor(patch(anyUrl()).willReturn(aResponse().withStatus(204)));

        jmsTemplate.send(inboundQueue, new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                BytesMessage bytesMessage = session.createBytesMessage();
                bytesMessage.writeBytes(largeEhrExtract.getBytes(StandardCharsets.UTF_8));
                return bytesMessage;
            }
        });
        sleep(5000);
        verify(getRequestedFor(urlMatching("/messages/8b373671-5884-45df-a22c-b3ef768e1dc4/72eaa355-b152-4b24-a088-ac2f66ae8a21")));
        verify(putRequestedFor(urlMatching("/ehr-storage")).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalTo(largeEhrExtract)));
        verify(postRequestedFor(urlMatching("/messages")));
        verify(patchRequestedFor(urlMatching("/deduction-requests/8b373671-5884-45df-a22c-b3ef768e1dc4/large-ehr-started")));
        jmsTemplate.setReceiveTimeout(1000);
        assertNull(jmsTemplate.receive(unhandledQueue));
    }

    @Test
    void shouldUploadAttachmentMessageToEhrRepoStorage() throws IOException, InterruptedException {
        String copcMessage = dataLoader.getDataAsString("COPC_IN000001UK01.xml");
        String url = String.format("%s/attachment-storage", wireMock.baseUrl());
        wireMock.stubFor(get(anyUrl()).willReturn(aResponse().withBody(url).withStatus(200)));
        wireMock.stubFor(put(urlMatching("/attachment-storage")).willReturn(aResponse().withStatus(200)));
        wireMock.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(201)));

        jmsTemplate.send(inboundQueue, new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                BytesMessage bytesMessage = session.createBytesMessage();
                bytesMessage.writeBytes(copcMessage.getBytes(StandardCharsets.UTF_8));
                return bytesMessage;
            }
        });
        sleep(5000);
        verify(putRequestedFor(urlMatching("/attachment-storage")).withRequestBody(com.github.tomakehurst.wiremock.client.WireMock.equalTo(copcMessage)));
        jmsTemplate.setReceiveTimeout(1000);
        assertNull(jmsTemplate.receive(unhandledQueue));
    }

    @Test
    void shouldCallGpToRepoWhenReceivedPdsUpdateCompleted() throws IOException, InterruptedException {
        String pdsUpdatedMessage = dataLoader.getDataAsString("PRPA_IN000202UK01.xml");
        String url = String.format("/deduction-requests/%s/pds-updated", "3b71eb7e-5f87-426d-ae23-e0eafeb60bd4");
        wireMock.stubFor(patch(urlMatching(url)).willReturn(aResponse().withStatus(204)));

        jmsTemplate.send(inboundQueue, new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                BytesMessage bytesMessage = session.createBytesMessage();
                bytesMessage.writeBytes(pdsUpdatedMessage.getBytes(StandardCharsets.UTF_8));
                return bytesMessage;
            }
        });
        sleep(5000);
        verify(patchRequestedFor(urlMatching(url)).withHeader("Authorization", new EqualToPattern("auth-key-1")));
        jmsTemplate.setReceiveTimeout(1000);
        assertNull(jmsTemplate.receive(unhandledQueue));
    }

    @Test
    void shouldSendRegistrationRequestToRepoToGp() throws IOException, InterruptedException {
        String registrationRequestMessage = dataLoader.getDataAsString("RCMR_IN010000UK05.xml");
        String conversationId = "dff5321c-c6ea-468e-bbc2-b0e48000e071";
        String ehrRequestId = "041CA2AE-3EC6-4AC9-942F-0F6621CC0BFC";
        String odsCode = "N82668";
        String nhsNumber = "9692294935";

        String requestBody = "{\"data\":{\"type\":\"registration-requests\",\"id\":\"" + conversationId + "\",\"attributes\":{\"ehrRequestId\":\"" + ehrRequestId + "\",\"odsCode\":\"" + odsCode + "\",\"nhsNumber\":\"" + nhsNumber + "\"}}}";
        wireMock.stubFor(post(urlMatching("/registration-requests")).willReturn(aResponse().withStatus(204)));

        jmsTemplate.send(inboundQueue, new MessageCreator() {
            @Override
            public Message createMessage(Session session) throws JMSException {
                BytesMessage bytesMessage = session.createBytesMessage();
                bytesMessage.writeBytes(registrationRequestMessage.getBytes(StandardCharsets.UTF_8));
                return bytesMessage;
            }
        });
        sleep(5000);
        verify(postRequestedFor(urlMatching("/registration-requests")).withRequestBody(equalToJson(requestBody)));
        jmsTemplate.setReceiveTimeout(1000);
        assertNull(jmsTemplate.receive(unhandledQueue));
    }
}