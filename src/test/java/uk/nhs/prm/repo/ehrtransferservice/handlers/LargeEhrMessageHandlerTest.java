package uk.nhs.prm.repo.ehrtransferservice.handlers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.prm.repo.ehrtransferservice.database.TransferTrackerService;
import uk.nhs.prm.repo.ehrtransferservice.models.LargeEhrMessage;
import uk.nhs.prm.repo.ehrtransferservice.repo_incoming.TransferTrackerDbEntry;
import uk.nhs.prm.repo.ehrtransferservice.services.ehr_repo.EhrRepoService;
import uk.nhs.prm.repo.ehrtransferservice.services.gp2gp_messenger.Gp2gpMessengerService;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LargeEhrMessageHandlerTest {

    @Mock
    EhrRepoService ehrRepoService;

    @Mock
    LargeEhrMessage largeEhrMessage;

    @Mock
    Gp2gpMessengerService gp2gpMessengerService;

    @Mock
    TransferTrackerDbEntry transferTrackerDbEntry;

    @Mock
    TransferTrackerService transferTrackerService;

    @InjectMocks
    LargeEhrMessageHandler largeEhrMessageHandler;

    @Test
    public void shouldCallEhrRepoServiceToStoreMessageForLargeEhr() throws Exception {
        when(largeEhrMessage.getConversationId()).thenReturn(UUID.randomUUID());
        largeEhrMessageHandler.handleMessage(largeEhrMessage);
        verify(ehrRepoService).storeMessage(largeEhrMessage);
    }

    @Test
    public void shouldCallGp2GpMessengerServiceToMakeContinueRequest() throws Exception {
        when(largeEhrMessage.getConversationId()).thenReturn(UUID.randomUUID());
        when(transferTrackerService.getEhrTransferData(largeEhrMessage.getConversationId().toString())).thenReturn(transferTrackerDbEntry);
        largeEhrMessageHandler.handleMessage(largeEhrMessage);
        verify(gp2gpMessengerService).sendContinueMessage(largeEhrMessage, transferTrackerDbEntry);
    }

    @Test
    public void shouldCallTransferTrackerDbToUpdateWithExpectedStatus() throws Exception {
        when(largeEhrMessage.getConversationId()).thenReturn(UUID.randomUUID());
        largeEhrMessageHandler.handleMessage(largeEhrMessage);
        verify(transferTrackerService).updateStateOfEhrTransfer(largeEhrMessage.getConversationId().toString(), "ACTION:LARGE_EHR_CONTINUE_REQUEST_SENT");
    }
}