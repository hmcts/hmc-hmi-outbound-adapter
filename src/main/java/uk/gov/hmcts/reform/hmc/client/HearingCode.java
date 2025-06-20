package uk.gov.hmcts.reform.hmc.client;

import java.util.Arrays;

public enum HearingCode {

    LISTED(100, "LISTED"),
    PENDING_RELISTING(6, "PENDING_RELISTING"),
    CLOSED(8, "CLOSED"),
    EXCEPTION(-1, "EXCEPTION");

    private int number;
    private String label;

    HearingCode(int number, String label) {
        this.number = number;
        this.label = label;
    }

    public static HearingCode getByLabel(String name) {
        return Arrays.stream(HearingCode.values())
            .filter(eachLinkType -> eachLinkType.label.equals(name)).findAny().orElse(null);
    }

}
