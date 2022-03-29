package uk.nhs.prm.repo.ehrtransferservice.handlers;

import uk.nhs.prm.repo.ehrtransferservice.gp2gp_message_models.ParsedMessage;

public interface MessageHandler {
    String getInteractionId();

    void handleMessage(ParsedMessage parsedMessage);
}
