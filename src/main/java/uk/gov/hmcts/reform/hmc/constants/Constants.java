package uk.gov.hmcts.reform.hmc.constants;

public final class Constants {

    private Constants() {
    }

    public static final String HEARING_ID = "hearing_id";
    public static final String  HMC_HMI_OUTBOUND_ADAPTER = "<Hmc hmi outbound adapter>";
    public static final String WRITE = "<WRITE>";
    public static final String READ = "<READ>";

    public static final String ERROR_PROCESSING_MESSAGE = "Error occurred during service bus processing. "
        + "Service:{} .Entity:{}. Method:{}. Hearing ID: {}.";
    public static final String HMC_TO_HMI = "hmc-to-hmi";
    public static final String HMC_FROM_HMI = "hmc-from-hmi";
    public static final String NOT_DEFINED = "NOT_DEFINED";
    public static final String MESSAGE_ERROR = "Error for message with id ";
    public static final String WITH_ERROR = " with error ";
    public static final String HMI = "hmi";
    public static final String HMC = "hmc";
    public static final String HMC_TO_HMI_AUTH = "HMC to HMI auth";
    public static final String FAILURE_STATUS = "500";
    public static final String SUCCESS_STATUS = "200";
    public static final String AMQP_CACHE = "com.azure.core.amqp.cache";
    public static final String AMQP_CACHE_VALUE = "true";
}
