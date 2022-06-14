package uk.nhs.prm.repo.ehrtransferservice.listeners;

import com.amazon.sqs.javamessaging.message.SQSTextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.prm.repo.ehrtransferservice.config.Tracer;
import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.ParsedMessage;
import uk.nhs.prm.repo.ehrtransferservice.handlers.NegativeAcknowledgementHandler;
import uk.nhs.prm.repo.ehrtransferservice.parser_broker.Parser;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NegativeAcknowledgementListenerTest {

    @Mock
    Tracer tracer;

    @Mock
    Parser parser;

    @Mock
    NegativeAcknowledgementHandler handler;

    @InjectMocks
    NegativeAcknowledgementListener listener;

    public NegativeAcknowledgementListenerTest() {
        parser = new Parser();
    }

    @Test
    void shouldPassMessageToHandlerAndAcknowledgeIt() throws Exception {
        var message = spy(new SQSTextMessage("payload"));
        var parsedMessage = new StubParsedMessage();
        when(parser.parse("payload")).thenReturn(parsedMessage);

        listener.onMessage(message);

        verify(parser).parse("payload");
        verify(tracer).setMDCContextFromSqs(message);
        verify(message, times(1)).acknowledge();
        verify(handler).handleMessage(parsedMessage);
    }

    @Test
    void shouldNotAcknowledgeMessageWhenAnExceptionOccursInParsing() throws Exception {
        var message = spy(new SQSTextMessage("bleuch"));

        when(parser.parse(anyString())).thenThrow(new IllegalArgumentException());

        listener.onMessage(message);

        verify(message, never()).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeMessageWhenAnExceptionOccursInHandling() throws Exception {
        var message = spy(new SQSTextMessage("boom"));

        doThrow(new IllegalArgumentException()).when(handler).handleMessage(any());

        listener.onMessage(message);

        verify(message, never()).acknowledge();
    }

    class StubParsedMessage extends ParsedMessage {
        public StubParsedMessage() {
            super(null, null, null);
        }
    }

}