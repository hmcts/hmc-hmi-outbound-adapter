package uk.gov.hmcts.reform.hmc.hmi.befta;

import uk.gov.hmcts.befta.DefaultTestAutomationAdapter;
import uk.gov.hmcts.befta.dse.ccd.TestDataLoaderToDefinitionStore;
import uk.gov.hmcts.befta.player.BackEndFunctionalTestScenarioContext;

public class OutboundAdapterTestAutomationAdapter extends DefaultTestAutomationAdapter {

    private TestDataLoaderToDefinitionStore loader = new TestDataLoaderToDefinitionStore(this);

    @Override
    public Object calculateCustomValue(BackEndFunctionalTestScenarioContext scenarioContext, Object key) {
        if (key.toString().startsWith("no_dynamic_injection_")) {
            return key.toString().replace("no_dynamic_injection_","");
        }
        return super.calculateCustomValue(scenarioContext, key);
    }

}
