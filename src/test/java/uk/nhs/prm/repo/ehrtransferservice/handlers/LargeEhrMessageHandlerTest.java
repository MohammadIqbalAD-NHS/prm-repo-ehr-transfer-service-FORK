package uk.nhs.prm.repo.ehrtransferservice.handlers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.nhs.prm.repo.ehrtransferservice.database.TransferTrackerService;
import uk.nhs.prm.repo.ehrtransferservice.models.LargeSqsMessage;
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
    LargeSqsMessage largeSqsMessage;

    @Mock
    Gp2gpMessengerService gp2gpMessengerService;

    @Mock
    TransferTrackerDbEntry transferTrackerDbEntry;

    @Mock
    TransferTrackerService transferTrackerService;

    @InjectMocks
    LargeEhrMessageHandler largeEhrMessageHandler;

    @BeforeEach
    public void setUp() throws Exception {
        when(largeSqsMessage.getConversationId()).thenReturn(UUID.randomUUID());
        when(largeSqsMessage.getMessageId()).thenReturn(UUID.randomUUID());
    }

    @Test
    public void shouldCallEhrRepoServiceToStoreMessageForLargeEhr() throws Exception {
        largeEhrMessageHandler.handleMessage(largeSqsMessage);
        verify(ehrRepoService).storeMessage(largeSqsMessage);
    }

    @Test
    public void shouldCallGp2GpMessengerServiceToMakeContinueRequest() throws Exception {
        when(transferTrackerService.getEhrTransferData(largeSqsMessage.getConversationId().toString())).thenReturn(transferTrackerDbEntry);
        largeEhrMessageHandler.handleMessage(largeSqsMessage);
        verify(gp2gpMessengerService).sendContinueMessage(largeSqsMessage, transferTrackerDbEntry);
    }

    @Test
    public void shouldCallTransferTrackerDbToUpdateWithExpectedStatus() throws Exception {
        largeEhrMessageHandler.handleMessage(largeSqsMessage);
        verify(transferTrackerService).updateStateOfEhrTransfer(largeSqsMessage.getConversationId().toString(), "ACTION:LARGE_EHR_CONTINUE_REQUEST_SENT");
    }

    @Test
    public void shouldCallTransferTrackerDbToUpdateWithLargeEhrCoreMessageId() throws Exception {
        largeEhrMessageHandler.handleMessage(largeSqsMessage);
        verify(transferTrackerService).updateLargeEhrCoreMessageId(largeSqsMessage.getConversationId().toString(), largeSqsMessage.getMessageId().toString());
    }
}