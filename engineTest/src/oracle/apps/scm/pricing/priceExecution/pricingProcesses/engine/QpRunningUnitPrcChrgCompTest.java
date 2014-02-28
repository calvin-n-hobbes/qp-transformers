package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import commonj.sdo.DataObject;

import commonj.sdo.helper.HelperContext;

import groovy.lang.GroovyClassLoader;

import java.math.BigDecimal;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import java.util.Map;

import java.util.Properties;

import java.util.Set;

import javax.sql.rowset.serial.SerialClob;

import oracle.adf.share.security.authentication.JAASAuthenticationService;

import oracle.apps.fnd.applcore.log.AppsLogger;

import oracle.apps.orderCapture.core.transformContext.publicService.ContextService;
import oracle.apps.orderCapture.core.transformContext.publicService.ContextServiceFactory;

import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject.Header;
import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject.Line;
import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject.PricingServiceParameter;

import oracle.jbo.ApplicationModule;
import oracle.jbo.AttributeList;
import oracle.jbo.NameValuePairs;
import oracle.jbo.Transaction;
import oracle.jbo.common.service.types.AmountTypeImpl;
import oracle.jbo.common.service.types.MeasureTypeImpl;

import oracle.jbo.domain.ClobDomain;

import oracle.jbo.server.AttributeListImpl;
import oracle.jbo.server.ViewObjectImpl;

import org.eclipse.persistence.sdo.SDOType;

import org.jtestcase.JTestCaseException;

import org.junit.Test;
import org.junit.Assert;
import org.junit.BeforeClass;

public class QpRunningUnitPrcChrgCompTest {
    private static JAASAuthenticationService jas;
    public QpRunningUnitPrcChrgCompTest() {
        super();
    }
    
    @BeforeClass
    public static void setUp() throws SQLException, JTestCaseException {
        
        jas = new JAASAuthenticationService(); 
        jas.login("PRICING_MANAGER_ALL_BU", "Welcome1");
        
        
    }
    

    
    private DataObject generateTestSDO() {
        long[] inventoryItemId = {149};
        
        // create SDO with help from context service
        ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject pri = (DataObject) contextSvc.createConsumer("Sales", "PriceSalesTransaction");

        DataObject psp = pri.createDataObject("PricingServiceParameter");
        psp.setString("PricingContext", "SALES");
        psp.setString("OutputStatus", "SUCCESS");
        DataObject hdr = pri.createDataObject("Header");
        hdr.setLong("HeaderId", 1);
        
        
        //add one line and charge to input sdo
        long id = 1;
        
        DataObject line = pri.createDataObject("Line");
        line.setLong("LineId", id);
        line.setLong("HeaderId", 1);
        line.setLong("InventoryItemId", 149);
        line.setLong("InventoryOrganizationId", 204);
        line.setLong("AppliedPriceListId", 1002L);
        MeasureTypeImpl lineQty = (MeasureTypeImpl) line.createDataObject("LineQuantity");
        lineQty.setValue(new BigDecimal(2));
        lineQty.setUnitCode("Ea");
        line.setString("LineQuantityUOMCode", "Ea");
        line.setString("LineTypeCode", "BUY");
        //line.setString("AppliedCurrencyCode", "USD");
        //line.set("PricingDate", new Timestamp(System.currentTimeMillis()));
        line.set("PriceAsOf", new Timestamp(System.currentTimeMillis()));
        line.setString("OverrideCurrencyCode", "EEX");
        line.setLong("PricingStrategyId", 2022000);
        

        DataObject ch = pri.createDataObject("Charge");
        ch.setLong("ChargeId", id);
        ch.setLong("ParentEntityId", id);
        MeasureTypeImpl qty = (MeasureTypeImpl) ch.createDataObject("PricedQuantity");
        qty.setValue(new BigDecimal(2));
        qty.setUnitCode("Ea");
        

        return pri;
    }
    
    /*private void runGroovyScript(String className, String filename, Map<String, Object> args, Global g) {
        String viewRoot = System.getenv("ADE_VIEW_ROOT");
        String filePath = "/fusionapps/scm/components/pricing/priceExecution/pricingProcesses/engine/src/oracle/apps/scm/pricing/priceExecution/pricingProcesses/script/";
        String pricingUtil = viewRoot + filePath + "PricingUtil.groovy";
        String applyPriceListAdjustment = viewRoot + filePath + "PriceListAdjustment.groovy";

        ScriptEngine engine = ScriptEngine.getInstance();

        if ( g==null ) {
            g = EngineTest.generateDefaultGlobalInstance();
        }
        args.put("global", g);

        engine.loadOrParseClass("ApplyPriceListAdjustment", applyPriceListAdjustment); // testing
        engine.loadOrParseClass("PricingUtil", pricingUtil);

        engine.runGroovyScriptFromFile(className, viewRoot + filePath + filename, args);
    }

    private Global generateDefaultGlobalInstance() {
        Global gb = new Global();
        gb.addVariable("runningUnitPrice", new HashMap<Long, Long>());
        gb.addVariable("chargeIdCounter", new Long(1));
        gb.addVariable("chargeComponentIdCounter", new Long(1));
        gb.addVariable("compSeqCounter", new HashMap<Long, Long>()); //Map to store ChargeComponent.SequenceNumber for a given ChargeId
        gb.addVariable("compValidDates", new HashMap<Long, Map<Long, Object>>()); //ChargeComponentId -> PriceValidFrom, PriceValidUntil
        //gb.addVariable("lineMessageTypeCodeMap", new HashMap<Long, String>());
        //gb.addVariable("headerMessageTypeCodeMap", new HashMap<Long, String>());
        
        Map headerPrefix = new HashMap<String, Object>();
        headerPrefix.put("errors", new HashSet<Long>()); // header.errors is a set containing IDs of all headers in error
        gb.addVariable("header", headerPrefix);

        Map linePrefix = new HashMap<String, Object>();
        linePrefix.put("errors", new HashSet<Long>()); // line.errors is a set containing IDs of all lines in error
        gb.addVariable("line", linePrefix);

        gb.addVariable("itemAttribute", new HashMap<List<Long>, Map<String, String>>()); // [inventory item ID, inventory org ID] -> {item number, service duration type, BOM item type}
        gb.addVariable("partyAttribute", new HashMap<Long, Map<String, String>>()); // party ID -> {party name, party type}
        gb.addVariable("partyOrgProfile", new HashMap<List, Map<String, String>>()); // party ID -> {GSA indicator flag, line of business}
        gb.addVariable("partyPersonProfile", new HashMap<List, Map<String, String>>()); // party ID -> {gender, marital status}
        return gb;
    }   */ 
    
    @Test
    public void testFileListPriceSuccess() {
        String getBaseListPrice = "QpGetBaseListPrice.groovy";
        String getListPrice = "QpRunningUnitPrcChrgComp.groovy";

        // initialize global variables
        Global gb = EngineTest.generateDefaultGlobalInstance();

        DataObject pri = generateTestSDO();
        
        
        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Input:\n" + EngineTest.saveSDOAsString(pri), AppsLogger.FINEST);
        }
        
        long id = 1;
        
        //instantiate globals
        Map chargesMap = (Map) gb.getProperty("charge");
        Map<String, Object> charge = new HashMap<String, Object>();
        charge.put("runningUnitPrice", new BigDecimal("16.24"));
        charge.put("compSeqCounter", 1000L);
        chargesMap.put(new Long(id), charge);
        

        // construct input arguments        
        GroovyDataObject sdo = new GroovyDataObject((DataObject) pri); //cast to GroovyDataObject
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("sdo", sdo);
        String priceElementCode = "T_LIST_PRICE";
        args.put("priceElementCode", priceElementCode);

        //runGroovyScript("GetBaseListPrice", getBaseListPrice, args, gb);
        EngineTest.runGroovyScript("GetListPrice", getListPrice, args, gb);
        
        pri = ((GroovyDataObject)args.get("sdo")).getDataObject();
        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Output:\n" + EngineTest.saveSDOAsString(pri), AppsLogger.FINEST);
        }
        
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) )
                AppsLogger.write(this, "Exception while sleep\n", AppsLogger.FINEST);
        }
        
        List comps = pri.getList("ChargeComponent");        
        Assert.assertTrue("Charge Component Count ",1 == comps.size());
        
        // check attributes on returned charge components
        for (int i=0; i< comps.size(); i++) {
            DataObject comp = (DataObject) comps.get(i);
            Assert.assertTrue("PriceElementCode",null != comp.getString("PriceElementCode") || comp.getString("PriceElementCode").isEmpty());
            if (priceElementCode.equals(comp.getString("PriceElementCode"))) {
                AmountTypeImpl unitPrice= (AmountTypeImpl) comp.get("UnitPrice");
                AmountTypeImpl extAmt= (AmountTypeImpl) comp.get("ExtendedAmount");
                Assert.assertTrue("UnitPrice",null != unitPrice && null != unitPrice.getValue() && null != unitPrice.getCurrencyCode());
                Assert.assertTrue("ExtendedAmount",null != extAmt && null != extAmt.getValue() && null != extAmt.getCurrencyCode());
                Long seq = comp.getLong("SequenceNumber");
                Assert.assertTrue("SequenceNumber",seq > 0);
            }
        }
        
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) )
                AppsLogger.write(this, "Exception while sleep\n", AppsLogger.FINEST);
        }
    }
    
    @Test
    public void testFileListPriceLineError() {
        String getListPrice = "QpRunningUnitPrcChrgComp.groovy";

        // initialize global variables
        Global gb = EngineTest.generateDefaultGlobalInstance();

        DataObject pri = generateTestSDO();
        
        
        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Input:\n" + EngineTest.saveSDOAsString(pri), AppsLogger.FINEST);
        }
        
        long id = 1;
        
        //instantiate globals
        Map chargesMap = (Map) gb.getProperty("charge");
        Map<String, Object> charge = new HashMap<String, Object>();
        charge.put("runningUnitPrice", new BigDecimal("16.24"));
        charge.put("compSeqCounter", 1000L);
        chargesMap.put(new Long(id), charge);
        ((Set) ((Map) gb.getProperty("invalid")).get("lines")).add(1L); // line 103 is in error
        

        // construct input arguments        
        GroovyDataObject sdo = new GroovyDataObject((DataObject) pri); //cast to GroovyDataObject
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("sdo", sdo);
        String priceElementCode = "T_LIST_PRICE";
        args.put("priceElementCode", priceElementCode);

        EngineTest.runGroovyScript("GetListPrice", getListPrice, args, gb);
        
        pri = ((GroovyDataObject)args.get("sdo")).getDataObject();
        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Output:\n" + EngineTest.saveSDOAsString(pri), AppsLogger.FINEST);
        }
        
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) )
                AppsLogger.write(this, "Exception while sleep\n", AppsLogger.FINEST);
        }
        
        List comps = pri.getList("ChargeComponent");        
        Assert.assertFalse("Charge Component Count ",1 == comps.size());
        
        // check attributes on returned charge components
        /*for (int i=0; i< comps.size(); i++) {
            DataObject comp = (DataObject) comps.get(i);
            Assert.assertTrue("PriceElementCode",null != comp.getString("PriceElementCode") || comp.getString("PriceElementCode").isEmpty());
            if (priceElementCode.equals(comp.getString("PriceElementCode"))) {
                AmountTypeImpl unitPrice= (AmountTypeImpl) comp.get("UnitPrice");
                AmountTypeImpl extAmt= (AmountTypeImpl) comp.get("ExtendedAmount");
                Assert.assertTrue("UnitPrice",null != unitPrice && null != unitPrice.getValue() && null != unitPrice.getCurrencyCode());
                Assert.assertTrue("ExtendedAmount",null != extAmt && null != extAmt.getValue() && null != extAmt.getCurrencyCode());
                Long seq = comp.getLong("SequenceNumber");
                Assert.assertTrue("SequenceNumber",seq > 0);
            }
        }*/
        
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) )
                AppsLogger.write(this, "Exception while sleep\n", AppsLogger.FINEST);
        }
    }

}
