package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject;

import commonj.sdo.DataObject;
import java.util.Map;
import java.sql.Timestamp;

public class Line {
    private DataObject lineDo;
    private Long lineId = 101L;
    private Long headerId = 1L;
    private Long inventoryItemId = 149L;
    private Long inventoryOrganizationId = 204L;
//    private Long appliedPriceListId = 1006L;
//    private String lineQuantityUOMCode = "Ea";
//    private String lineTypeCode = "BUY";
//    private String appliedCurrencyCode = "USD";
    private Timestamp priceAsOf = new Timestamp(System.currentTimeMillis());
    private Timestamp pricedOn = new Timestamp(System.currentTimeMillis());
    private Timestamp transactionOn = new Timestamp(System.currentTimeMillis());

    public Line(DataObject consumer) {
        lineDo = consumer.createDataObject("Line");
        setLineId(lineId);
        setHeaderId(headerId);
        setInventoryItemId(inventoryItemId);
        setInventoryOrganizationId(inventoryOrganizationId);
        setPriceAsOf(priceAsOf);
        setPricedOn(pricedOn);
        setTransactionOn(transactionOn);        
    }

    public Line(DataObject consumer, Map<String, Object> params) {
        lineDo = consumer.createDataObject("Line");
        setLineId(params.get("LineId") != null ? (Long)params.get("LineId") : lineId);
        setHeaderId(params.get("HeaderId") != null ? (Long)params.get("HeaderId") : headerId);
        setInventoryItemId(params.get("InventoryItemId") != null ? (Long)params.get("InventoryItemId") : inventoryItemId);
        setInventoryOrganizationId(params.get("InventoryOrganizationId") != null ? (Long)params.get("InventoryOrganizationId") : inventoryOrganizationId);
//        setAppliedPriceListId(params.get("AppliedPriceListId") != null ? Long.parseLong(params.get("AppliedPriceListId").toString()) : appliedPriceListId);
//        setLineQuantityUOMCode(params.get("LineQuantityUOMCode") != null ? (String)params.get("LineQuantityUOMCode") : lineQuantityUOMCode);
//        setLineTypeCode(params.get("LineTypeCode") != null ? (String)params.get("LineTypeCode") : lineTypeCode);
//        setAppliedCurrencyCode(params.get("AppliedCurrencyCode") != null ? (String)params.get("AppliedCurrencyCode") : appliedCurrencyCode);        
        setPriceAsOf(params.get("PriceAsOf") != null ? (Timestamp)params.get("PriceAsOf") : priceAsOf);
        setPricedOn(params.get("PricedOn") != null ? (Timestamp)params.get("PricedOn") : pricedOn);
        setTransactionOn(params.get("TransactionOn") != null ? (Timestamp)params.get("TransactionOn") : transactionOn);
    }        
    
    public void setLineId(Long lineId) {
        lineDo.setLong("LineId", lineId);
    }

    public void setHeaderId(Long headerId) {
        lineDo.setLong("HeaderId", headerId);        
    }

    public void setInventoryItemId(Long inventoryItemId) {
        lineDo.setLong("InventoryItemId", inventoryItemId);                
    }

    public void setInventoryOrganizationId(Long inventoryOrganizationId) {
        lineDo.setLong("InventoryOrganizationId", inventoryOrganizationId);        
    }

//    public void setAppliedPriceListId(Long appliedPriceListId) {
//        lineDo.setLong("AppliedPriceListId", appliedPriceListId);                
//    }

//    public void setLineQuantityUOMCode(String lineQuantityUOMCode) {
//        lineDo.setString("LineQuantityUOMCode", lineQuantityUOMCode);        
//    }
//
//    public void setLineTypeCode(String lineTypeCode) {
//        lineDo.setString("LineTypeCode", lineTypeCode);        
//    }
//
//    public void setAppliedCurrencyCode(String appliedCurrencyCode) {
//        lineDo.setString("AppliedCurrencyCode", appliedCurrencyCode);                
//    }

    public void setPriceAsOf(Timestamp priceAsOf) {
        lineDo.set("PriceAsOf", priceAsOf);
    }
    
    public void setPricedOn(Timestamp pricedOn) {
        lineDo.set("PricedOn", pricedOn);
    }

    public void setTransactionOn(Timestamp transactionOn) {
        lineDo.set("TransactionOn", transactionOn);        
    }

    public DataObject getDataObject() {
        return lineDo;
    }
}
