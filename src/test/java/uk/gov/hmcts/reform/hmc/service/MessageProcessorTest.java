package uk.gov.hmcts.reform.hmc.service;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.hmc.data.PendingRequestEntity;
import uk.gov.hmcts.reform.hmc.errorhandling.ServiceBusMessageErrorHandler;
import uk.gov.hmcts.reform.hmc.repository.DefaultFutureHearingRepository;
import uk.gov.hmcts.reform.hmc.repository.PendingRequestRepository;

import java.util.Map;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {

    @Mock
    private DefaultFutureHearingRepository futureHearingRepository;


    @Mock
    private PendingRequestRepository pendingRequestRepository;

    @InjectMocks
    private MessageProcessor messageProcessor;

    private ServiceBusReceivedMessageContext messageContext;

    @Mock
    private ServiceBusMessageErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        ServiceBusReceivedMessage message = mock(ServiceBusReceivedMessage.class);
        messageContext = mock(ServiceBusReceivedMessageContext.class);

        when(messageContext.getMessage()).thenReturn(message);
        when(message.getApplicationProperties()).thenReturn(Map.of("hearing_id", "12345"));
        when(message.getBody()).thenReturn(BinaryData.fromString("{\"hearingRequest\":{\"listing\":{\"listingAutoCreateFlag\":false,\"listingPriority\":\"Standard\",\"listingType\":\"AAA7-APP\",\"listingDuration\":360,\"listingNumberAttendees\":10,\"listingPrivateFlag\":false,\"listingJohs\":[{\"listingJohId\":\"4927459\",\"listingJohPreference\":\"MUSTINC\"}],\"listingHearingChannels\":[\"INTER\"],\"listingLocations\":[{\"locationType\":\"Court\",\"locationId\":\"103147\",\"locationReferenceType\":\"EPIMS\"},{\"locationType\":\"Court\",\"locationId\":\"245068\",\"locationReferenceType\":\"EPIMS\"}],\"listingMultiDay\":{\"weeks\":0,\"days\":2,\"hours\":6},\"listingLanguage\":\"cym\",\"listingRoomAttributes\":[\"14\"]},\"entities\":[{\"entityId\":\"33083f7c-4798-44\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"CLAI\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"claimant1\",\"entityLastName\":\"UAT4\",\"entitySensitiveClient\":false},\"entityUnavailableDates\":[{\"unavailableStartDate\":\"2024-12-28T00:00:00Z\",\"unavailableEndDate\":\"2024-12-28T00:00:00Z\",\"unavailableType\":\"All Day\"},{\"unavailableStartDate\":\"2025-01-02T00:00:00Z\",\"unavailableEndDate\":\"2025-01-02T00:00:00Z\",\"unavailableType\":\"All Day\"},{\"unavailableStartDate\":\"2025-01-08T00:00:00Z\",\"unavailableEndDate\":\"2025-01-08T00:00:00Z\",\"unavailableType\":\"All Day\"}],\"entityHearingChannel\":\"INTER\"},{\"entityId\":\"14cf8533-b105-45\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"EXPR\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"claimant1\",\"entityLastName\":\"expert\",\"entitySensitiveClient\":false},\"entityHearingChannel\":\"INTER\"},{\"entityId\":\"ed61e646-2adb-47\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"EXPR\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"michael\",\"entityLastName\":\"brown\",\"entitySensitiveClient\":false},\"entityCommunications\":[{\"entityCommunicationDetails\":\"mickbrown@yahoo.com\",\"entityCommunicationType\":\"EMAIL\"}],\"entityHearingChannel\":\"INTER\"},{\"entityId\":\"aa63175d-9235-47\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"WITN\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"bethany\",\"entityLastName\":\"green\",\"entitySensitiveClient\":false},\"entityCommunications\":[{\"entityCommunicationDetails\":\"greenb@yahoo.com\",\"entityCommunicationType\":\"EMAIL\"}],\"entityHearingChannel\":\"INTER\"},{\"entityId\":\"3cd8dcac-2abf-4f\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"WITN\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"john\",\"entityLastName\":\"brown\",\"entitySensitiveClient\":false},\"entityCommunications\":[{\"entityCommunicationDetails\":\"brownjbrown@gmail.co.uk\",\"entityCommunicationType\":\"EMAIL\"},{\"entityCommunicationDetails\":\"01942 123456\",\"entityCommunicationType\":\"PHONE\"}],\"entityHearingChannel\":\"INTER\"},{\"entityId\":\"e06ed093-04fd-41\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"EXPR\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"david\",\"entityLastName\":\"smith\",\"entitySensitiveClient\":false},\"entityCommunications\":[{\"entityCommunicationDetails\":\"davidsmith@justice.gov.uk\",\"entityCommunicationType\":\"EMAIL\"}],\"entityHearingChannel\":\"INTER\"},{\"entityId\":\"6d7a045e-392b-40\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"WITN\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"melissa\",\"entityLastName\":\"jones\",\"entitySensitiveClient\":false,\"entitySpecialNeedsOther\":\"RA0010: Documents in a specified colour: uat4 needs yellow paper\"},\"entityCommunications\":[{\"entityCommunicationDetails\":\"mjones@justice.gov.uk\",\"entityCommunicationType\":\"EMAIL\"}],\"entityHearingChannel\":\"INTER\",\"entityOtherConsiderations\":[\"RA0010\"]},{\"entityId\":\"3ad3d351-d5af-4a\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"LGRP\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"will\",\"entityLastName\":\"smith\",\"entitySensitiveClient\":false},\"entityCommunications\":[{\"entityCommunicationDetails\":\"freshprince@email.com\",\"entityCommunicationType\":\"EMAIL\"},{\"entityCommunicationDetails\":\"07890123456\",\"entityCommunicationType\":\"PHONE\"}],\"entityHearingChannel\":\"INTER\"},{\"entityId\":\"a767e26f-720d-4e\",\"entityTypeCode\":\"IND\",\"entityRoleCode\":\"LGRP\",\"entitySubType\":{\"entityClassCode\":\"PERSON\",\"entityFirstName\":\"lucy\",\"entityLastName\":\"smith\",\"entitySensitiveClient\":false},\"entityCommunications\":[{\"entityCommunicationDetails\":\"lucys@email.com\",\"entityCommunicationType\":\"EMAIL\"},{\"entityCommunicationDetails\":\"0161 234 5678\",\"entityCommunicationType\":\"PHONE\"}],\"entityHearingChannel\":\"INTER\"},{\"entityId\":\"B04IXE4\",\"entityTypeCode\":\"ORG\",\"entityRoleCode\":\"LGRP\",\"entitySubType\":{\"entityClassCode\":\"ORG\",\"entityCompanyName\":\"Civil - Organisation 1\"}},{\"entityId\":\"4feb2f44-a8c9-4f\",\"entityTypeCode\":\"ORG\",\"entityRoleCode\":\"DEFE\",\"entitySubType\":{\"entityClassCode\":\"ORG\",\"entityCompanyName\":\"browns\"}},{\"entityId\":\"DAWY9LJ\",\"entityTypeCode\":\"ORG\",\"entityRoleCode\":\"LGRP\",\"entitySubType\":{\"entityClassCode\":\"ORG\",\"entityCompanyName\":\"Civil - Organisation 2\"}},{\"entityId\":\"a5430d4e-a636-4e\",\"entityTypeCode\":\"ORG\",\"entityRoleCode\":\"DEFE\",\"entitySubType\":{\"entityClassCode\":\"ORG\",\"entityCompanyName\":\"defendant2_company_UAT4\"}}],\"_case\":{\"caseListingRequestId\":\"2000010807\",\"caseTitle\":\"'claimant1 UAT4' v 'browns', 'defendant2_company_UAT4'\",\"caseJurisdiction\":\"AA\",\"caseRegistered\":\"2024-04-26T00:00:00Z\",\"caseCourt\":{\"locationType\":\"Court\",\"locationId\":\"245068\",\"locationReferenceType\":\"EPIMS\"},\"caseClassifications\":[{\"caseClassificationService\":\"AAA7\",\"caseClassificationType\":\"AAA7-FAST_CLAIM\",\"caseClassificationSubType\":\"AAA7-FAST_CLAIM\"}],\"caseInterpreterRequiredFlag\":false,\"caseRestrictedFlag\":false,\"caseVersionId\":1,\"caseLinks\":[{\"url\":\"https://manage-case.demo.platform.hmcts.net/cases/case-details/1714142979360133\"}],\"casePublishedName\":\"'claimant1 UAT4' v 'browns', 'defendant2_company_UAT4'\",\"caseAdditionalSecurityFlag\":false,\"linkedHearingGroupStatus\":\"Not Required\",\"caseIdHMCTS\":\"1714142979360133\"}}}"));
    }

    @Test
    void shouldAddToPendingRequestsOnProcessingFailure() {
        doThrow(new RuntimeException("Processing failure")).when(futureHearingRepository).createHearingRequest(any());
        doNothing().when(errorHandler).handleGenericError(any(ServiceBusReceivedMessageContext.class), any(Exception.class));
        messageProcessor.processMessage(messageContext);
        verify(pendingRequestRepository, times(1)).save(any(PendingRequestEntity.class));
        verify(messageContext, never()).complete();
    }

    @Test
    void shouldNotAddToPendingRequestsOnSuccess() {
        doNothing().when(futureHearingRepository).createHearingRequest(any());
        messageProcessor.processMessage(messageContext);
        verify(pendingRequestRepository, never()).save(any(PendingRequestEntity.class));
        verify(messageContext, times(1)).complete();
    }
}
