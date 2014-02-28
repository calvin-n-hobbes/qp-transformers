  package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject;

import commonj.sdo.DataObject;
import java.util.Map;

public class PricingServiceParameter {    
    private DataObject pspDo;    
    private String pricingContext = "SALES";
    private String outputStatus = "SUCCESS";
    
    public PricingServiceParameter(DataObject consumer) {
        pspDo = consumer.createDataObject("PricingServiceParameter");
        setPricingContext(pricingContext);
        setOutputStatus(outputStatus);
    }
    
    public PricingServiceParameter(DataObject consumer, Map<String, Object> params) {
        pspDo = consumer.createDataObject("PricingServiceParameter");
        setPricingContext(params.get("PricingContext") != null ? (String)params.get("PricingContext") : pricingContext);
        setOutputStatus(params.get("OutputStatus") != null ? (String)params.get("OutputStatus") : outputStatus);
    }

    public void setPricingContext(String pricingContext) {        
        pspDo.setString("PricingContext", pricingContext);
    }

    public void setOutputStatus(String outputStatus) {        
        pspDo.setString("OutputStatus", outputStatus);
    }

    public DataObject getDataObject() {
        return pspDo;
    }
}
