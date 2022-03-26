package uk.nhs.prm.repo.ehrtransferservice.ehrrequesthandler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class RepoIncomingEventParserTest {

    @Test
    void shouldParseRepoIncomingMessageCorrectlyWhenAMessageContainsExpectedValues() {
        String incomingMessage = "{\"nhsNumber\":\"nhs-number\",\"sourceGP\":\"source-gp\",\"nemsMessageId\":\"nems-message-id\",\"destinationGP\":\"destination-GP\"}";
        var repoIncomingEventParser = new RepoIncomingEventParser();
        var parsedMessage = repoIncomingEventParser.parse(incomingMessage);
        assertEquals("nhs-number", parsedMessage.getNhsNumber());
    }

    @Test
    void shouldThrowAnExceptionWhenItTriesToParseAGarbageMessage() {
        String incomingMessage = "invalid";
        var repoIncomingEventParser = new RepoIncomingEventParser();
        assertThrows(RuntimeException.class, () -> repoIncomingEventParser.parse(incomingMessage));
    }

}