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
import java.util.Calendar;
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

public class QpSetFinalValuesTest {
    private static JAASAuthenticationService jas;
    public QpSetFinalValuesTest() {
        super();
    }
    
    // Placeholder test to always return successfully
    //@Test
    public void dummyTest() {
        return;
    }
    
    @BeforeClass
    public static void setUp() throws SQLException, JTestCaseException {
        
        jas = new JAASAuthenticationService(); 
        jas.login("PRICING_MANAGER_ALL_BU", "Welcome1");
        
        
    }
    

    
    private DataObject generateTestSDO(int numOfLines, int numOfCharges) {
        long[] inventoryItemId = {149};
        
        // create SDO with help from context service
        ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject sdo = (DataObject) contextSvc.createConsumer("Sales", "PriceSalesTransaction");

        DataObject psp = sdo.createDataObject("PricingServiceParameter");
        psp.setString("PricingContext", "SALES");
        psp.setString("OutputStatus", "SUCCESS");
        DataObject hdr = sdo.createDataObject("Header");
        hdr.setLong("HeaderId", 1);
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "NumLines " + numOfLines+" NumCharges " + numOfCharges, AppsLogger.FINEST);
        }
        
        /*DataObject ch = sdo.createDataObject("Charge");
        ch.setLong("ChargeId", 1);
        ch.setString("ParentEntityCode", "LINE");
        ch.setLong("ParentEntityId", 1);

        ch = sdo.createDataObject("Charge");
        ch.setLong("ChargeId", 10);
        ch.setString("ParentEntityCode", "LINE");
        ch.setLong("ParentEntityId", 1);*/
        
        long chargeId = 0;
                
        for (int i=1; i<= numOfLines; i++) {
            /*if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "Iterator i " + i, AppsLogger.FINEST);
            }*/
            
            DataObject line = sdo.createDataObject("Line");
            line.setLong("LineId", numOfLines-i+1);
            line.setLong("HeaderId", 1);
            line.setLong("InventoryItemId", i+149);
            
            
            for (int j=1; j<=numOfCharges; j++) {
                /*if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                    AppsLogger.write(this, "Iterator j " + j, AppsLogger.FINEST);
                }  */
                

                DataObject ch = sdo.createDataObject("Charge");
                ch.setLong("ChargeId", ++chargeId);
                ch.setString("ParentEntityCode", "LINE");
                ch.setLong("ParentEntityId", numOfLines-i+1);
                
                DataObject chComp = sdo.createDataObject("ChargeComponent");
                chComp.setLong("ChargeId", chargeId);
                chComp.setLong("ChargeComponentId", chargeId);
                chComp.setString("PriceElementCode", "LIST_PRICE");
                AmountTypeImpl unitPrice = (AmountTypeImpl) chComp.createDataObject("UnitPrice");
                unitPrice.setValue(new BigDecimal(100*(numOfCharges-j+1)));
                unitPrice.setCurrencyCode("USD");
            }
        }
        

        return sdo;
    }
    
   
    

    
    //@Test
    public void invokeProcess() {
        DataObject sdoA = generateTestSDO(1,2);
        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Input:\n" + EngineTest.saveSDOAsString(sdoA), AppsLogger.FINEST);
        }
        
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("priceRequest", sdoA);
        args = Executor.run("QP_PRICE_SALES_TRANSACTION", args);
        
        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Output:\n" + EngineTest.saveSDOAsString(sdoA), AppsLogger.FINEST);
        }
        
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) )
                AppsLogger.write(this, "Exception while sleep\n", AppsLogger.FINEST);
        }
    }
   




    /*private void runGroovyScript(String className, String filename, Map<String, Object> args, Global g) {
        String viewRoot = System.getenv("ADE_VIEW_ROOT");
        String filePath = "/fusionapps/scm/components/pricing/priceExecution/pricingProcesses/engine/src/oracle/apps/scm/pricing/priceExecution/pricingProcesses/script/";
        String pricingUtil = viewRoot + filePath + "PricingUtil.groovy";
        String applyPriceListAdjustment = viewRoot + filePath + "PriceListAdjustment.groovy";

        ScriptEngine engine = ScriptEngine.getInstance();

        if ( g==null ) {
            g = generateDefaultGlobalInstance();
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
        gb.addVariable("compValidDates", new HashMap<Long, Map<Long, Timestamp>>()); //ChargeComponentId -> PriceValidFrom, PriceValidUntil
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
    }*/    
    

    /**
     * Test the Groovy class loader cache by parsing a one-line script and
     * loading the same class referenced by binary name.
     */
    @Test
    public void testFileSetFinalValues() {
        String setFinalValues = "QpSetFinalValues.groovy";

        // initialize global variables
        Global gb = EngineTest.generateDefaultGlobalInstance();
        ((Set) ((Map) gb.getProperty("invalid")).get("lines")).add(2L); // line 2 is in error
        
        //instantiate globals
        Map compsMap = (Map) gb.getProperty("chargeComponent");
        for (int i=1; i<=3; i++) {
            Map<String, Object> comp = new HashMap<String, Object>();
            Calendar c1 = Calendar.getInstance();
            c1.set(2000,Calendar.JANUARY,i,1,1);
            java.util.Date stdt = c1.getTime();
            Timestamp stdttime = new Timestamp(stdt.getTime());
            comp.put("priceValidFrom", stdttime);
            c1.set(2000,Calendar.JANUARY,3-i,1,1);
            java.util.Date enddt = c1.getTime();
            Timestamp enddttime = new Timestamp(enddt.getTime());
            comp.put("priceValidUntil", enddttime);
            compsMap.put(new Long(i+3),comp);
        }
        
        //if ( AppsLogger.isEnabled(AppsLogger.FINEST) )
            //AppsLogger.write(this, "Print ValidDates: 1L "+compValidDatesMap., AppsLogger.FINEST);

        // create SDO with help from context service
        /*ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject pri = (DataObject) contextSvc.createConsumer("Sales", "PriceRequestInternal");*/

        // construct input arguments
        Map<String, Object> args = new HashMap<String, Object>();
        DataObject sdo = generateTestSDO(2,3);
        args.put("sdo", new GroovyDataObject(sdo));
        args.put("comparisonElementCode", "LIST_PRICE");
        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Input:\n" + EngineTest.saveSDOAsString(sdo), AppsLogger.FINEST);
        }

        EngineTest.runGroovyScript("SetFinalValues", setFinalValues, args, gb);
        
        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Output:\n" + EngineTest.saveSDOAsString(sdo), AppsLogger.FINEST);
        }
        

        
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) )
                AppsLogger.write(this, "Exception while sleep\n", AppsLogger.FINEST);
        }
        
        List charges = sdo.getList("Charge");
        List comps = sdo.getList("ChargeComponent");        
        Assert.assertTrue("Charge Component Count ",3 == charges.size());
        Assert.assertTrue("Charge Component Count ",3 == comps.size());
        DataObject line = (DataObject) sdo.getList("Line").get(1);
        Assert.assertTrue("Line ValidFrom and Until", null != ((Timestamp)line.get("PriceValidFrom")) && null != ((Timestamp)line.get("PriceValidUntil")));
        DataObject hdr = (DataObject) sdo.getList("Header").get(0);
        Assert.assertTrue("Header ValidFrom and Until", null != ((Timestamp)hdr.get("PriceValidFrom")) && null != ((Timestamp)hdr.get("PriceValidUntil")));
        
        // check attributes on returned charge components
        for (int i=0; i< comps.size(); i++) {
            DataObject comp = (DataObject) comps.get(i);
            Assert.assertTrue("PercentOfComparisonElement",null != comp.getString("PercentOfComparisonElement"));
        }
    }
}
