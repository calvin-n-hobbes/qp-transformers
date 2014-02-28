package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject;

import commonj.sdo.DataObject;

import java.sql.Timestamp;

import java.util.Map;

public class Header {
    private DataObject headerDo;
    private Long headerId = 1L;
    private Long customerId = 1006L;
    private Long sellingBusinessUnitId = 204L;
    private Timestamp priceAsOf = new Timestamp(System.currentTimeMillis()); 
    private Timestamp transactionOn = new Timestamp(System.currentTimeMillis());
    
    public Header(DataObject consumer) {
        headerDo = consumer.createDataObject("Header");
        setHeaderId(headerId);        
        setCustomerId(customerId);
        setSellingBusinessUnitId(sellingBusinessUnitId);
        setPriceAsOf(priceAsOf);
        setTransactionOn(transactionOn);
    }
    
    public Header(DataObject consumer, Map<String, Object> params) {
        headerDo = consumer.createDataObject("Header");
        setHeaderId(params.get("HeaderId") != null ? (Long)params.get("HeaderId") : headerId);
        setCustomerId(params.get("CustomerId") != null ? (Long)params.get("CustomerId") : customerId);
        setSellingBusinessUnitId(params.get("SellingBusinessUnitId") != null ? (Long)params.get("SellingBusinessUnitId") : sellingBusinessUnitId);
        setPriceAsOf(params.get("PriceAsOf") != null ? (Timestamp)params.get("PriceAsOf") : priceAsOf);
        setTransactionOn(params.get("TransactionOn") != null ? (Timestamp)params.get("TransactionOn") : transactionOn);
    }    

    public void setHeaderId(Long headerId) {        
        headerDo.setLong("HeaderId", headerId);
    }

    public void setCustomerId(Long customerId) {
        headerDo.setLong("CustomerId", customerId);        
    }
    
    public void setSellingBusinessUnitId(Long sellingBusinessUnitId) {        
        headerDo.setLong("SellingBusinessUnitId", sellingBusinessUnitId);
    }

    public void setPriceAsOf(Timestamp priceAsOf) {
        headerDo.set("PriceAsOf", priceAsOf);        
    }

    public void setTransactionOn(Timestamp transactionOn) {
        headerDo.set("TransactionOn", transactionOn);        
    }

    public DataObject getDataObject() {
        return headerDo;
    }
}
