package uk.gov.hmcts.reform.hmc.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HearingCodeTest {

    @Test
    void testGetByLabel() {
        assertEquals(HearingCode.LISTED, HearingCode.getByLabel("LISTED"));
        assertEquals(HearingCode.PENDING_RELISTING, HearingCode.getByLabel("PENDING_RELISTING"));
        assertEquals(HearingCode.CLOSED, HearingCode.getByLabel("CLOSED"));
        assertEquals(HearingCode.EXCEPTION, HearingCode.getByLabel("EXCEPTION"));
        assertNull(HearingCode.getByLabel("INVALID_LABEL"));
    }
}


