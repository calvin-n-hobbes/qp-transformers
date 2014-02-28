package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import commonj.sdo.DataObject;

import commonj.sdo.helper.HelperContext;

import groovy.lang.GroovyClassLoader;

import java.math.BigDecimal;

import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import java.util.Map;
import java.util.Set;

import oracle.apps.fnd.applcore.log.AppsLogger;
import oracle.adf.share.security.authentication.JAASAuthenticationService;

import oracle.apps.orderCapture.core.transformContext.publicService.ContextService;
import oracle.apps.orderCapture.core.transformContext.publicService.ContextServiceFactory;

import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject.Header;
import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject.Line;
import oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.dataObject.PricingServiceParameter;

import oracle.jbo.ApplicationModule;
import oracle.jbo.NameValuePairs;
import oracle.jbo.Transaction;
import oracle.jbo.common.service.types.AmountTypeImpl;
import oracle.jbo.common.service.types.MeasureTypeImpl;

import oracle.jbo.domain.ClobDomain;

import oracle.jbo.server.ViewObjectImpl;

import org.eclipse.persistence.sdo.SDOType;

import org.junit.Test;
import org.junit.Assert;

public class EngineTest {
    public EngineTest() {
        super();
    }
    
    // Placeholder test to always return successfully
    //@Test
    public void dummyTest() {
        return;
    }
    
    /**
     * Renders SDO as string
     * @see PriceRequestTest#saveSDOAsString(commonj.sdo.DataObject)
     * @return(commonj.sdo.DataObject)
     */
    public static String saveSDOAsString(DataObject inputSDO) {
        if ( inputSDO==null || inputSDO.getType()==null ) {
            return "null";
        }

        SDOType type = (SDOType) inputSDO.getType();
        HelperContext hCtx = type.getHelperContext();
        String outputMsg;
        try {
            outputMsg = hCtx.getXMLHelper().save(inputSDO, type.getURI(), type.getName());
        }
        catch (RuntimeException e) {
            /*if ( isDebugMode ) {
                throw e;
            }*/
            outputMsg = "Failure saving SDO as string: ";
            if ( type!=null && type.getName()!=null ) {
                outputMsg += type.getName();
            }
            outputMsg += "\n" + e.getMessage();
        }

        return outputMsg;
    }

    private static DataObject generateTestSDO(int numOfLines) {
        long[] inventoryItemId = {149, 187, 201, 213, 189, 2155, 155, 8313};

        // create SDO with help from context service
        ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject pri = (DataObject) contextSvc.createConsumer("Sales", "PriceSalesTransaction");

        DataObject psp = pri.createDataObject("PricingServiceParameter");
        psp.setString("PricingContext", "SALES");
        psp.setString("OutputStatus", "SUCCESS");
        
        DataObject hdr = pri.createDataObject("Header");
        hdr.setLong("HeaderId", 1);
        hdr.setLong("SellingBusinessUnitId", 204);
        hdr.setLong("PricingStrategyId", 111);
        hdr.setLong("CustomerId", 1006);
        hdr.setString("OverrideCurrencyCode", "JPY");  
        hdr.set("PriceAsOf", new Timestamp(System.currentTimeMillis()));

        for (int n=0; n<numOfLines; n++) {
            long itemId = inventoryItemId[n % inventoryItemId.length];
            DataObject line = pri.createDataObject("Line");
            line.setLong("LineId", 101+n);
            line.setLong("HeaderId", 1);
            line.setLong("InventoryItemId", itemId);
            line.setLong("InventoryOrganizationId", 204L);
            line.setLong("AppliedPriceListId", 1002);
            MeasureTypeImpl lineQty = (MeasureTypeImpl) line.createDataObject("LineQuantity");
            lineQty.setValue(new BigDecimal(2));
            lineQty.setUnitCode("Ea");
            line.setString("LineQuantityUOMCode", "Ea");
            line.setString("LineTypeCode", "BUY");            
            line.set("PriceAsOf", new Timestamp(System.currentTimeMillis()));
            line.setString("OverrideCurrencyCode", "USD");
            line.setLong("PricingStrategyId", 111);
        }
        
        return pri;
    }
    
    private static DataObject generateTestSDO(int appliedPriceListId, long[] itemIds, int numOfLines) {
        // long[] inventoryItemId = {149, 187, 201, 213, 189, 2155, 155, 8313};
        long[] inventoryItemId = itemIds;
        
        // create SDO with help from context service
        ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject pri = (DataObject) contextSvc.createConsumer("Sales", "PriceRequestInternal");        

        new PricingServiceParameter(pri);        
        new Header(pri);
        

        for (int n=0; n<numOfLines; n++) {
            long itemId = inventoryItemId[n % inventoryItemId.length];

            // Dev: Akshay(12/9/2013)
            // 
            // New way of defining a Line DataObject
            Line line  = new Line(pri);
            line.setLineId(101L + n);
            line.setInventoryItemId(itemId);
            
            DataObject lineDO = line.getDataObject();
            MeasureTypeImpl lineQty = (MeasureTypeImpl) lineDO.createDataObject("LineQuantity");
            lineQty.setValue(new BigDecimal(2));
            lineQty.setUnitCode("Ea");
            
            if ( n<inventoryItemId.length ) {
                DataObject itemAttr = pri.createDataObject("ItemAttribute");
                itemAttr.setLong("InventoryItemId", itemId);
                itemAttr.setLong("InventoryOrganizationId", 204);
            }
        }

        return pri;
    }
   
    
   // @Test --- DO NOT Use this, it is deprecated....
    public void testEngine() {
        int repetitions = 2; // minimum 2, because the first run is thrown out
        int numOfLines = 1;
        long total = 0;

        boolean runGroovy = true, runSTS = false;
        long groovyAvg, stsAvg = 0;
        List groovyTime = new ArrayList(), stsTime = new ArrayList();
        
        /* Groovy engine */
        String viewRoot = System.getenv("ADE_VIEW_ROOT");
        String filePath = "/fusionapps/scm/components/pricing/priceExecution/pricingProcesses/engine/src/oracle/apps/scm/pricing/priceExecution/pricingProcesses/script/";
        String global = viewRoot + filePath + "Global.groovy";
        String getBaseListPrice = viewRoot + filePath + "BasePrice.groovy";
        String getListPrice = viewRoot + filePath + "ListPrice.groovy";
        String pricingUtil = viewRoot + filePath + "PricingUtil.groovy";
        String applyPriceListAdjustment = viewRoot + filePath + "PriceListAdjustment.groovy";
        String derivePriceListCurr = viewRoot + filePath + "DerivePriceListCurrency.groovy";

        ScriptEngine pEng = ScriptEngine.getInstance();
        pEng.init();

        /* initialize global variables */
        Global gb = new Global();
        ProcessController.getInstance().registerGlobalInstance(gb);
        gb.addVariable("runningUnitPrice", new HashMap<Long, Long>());
        gb.addVariable("dummylong", new Long((long)(Math.random() * 100)));

        //pEng.loadOrParseClass("Global", global); // not the correct way to do this
        pEng.loadOrParseClass("ApplyPriceListAdjustment", applyPriceListAdjustment); // testing
        pEng.loadOrParseClass("PricingUtil", pricingUtil);
        
        for (int i=0; i<repetitions; ++i) {
            DataObject sdoA = generateTestSDO(numOfLines);
            long elapsed = 0;
            //elapsed += pEng.runStepFromFile(sdoA, "DerivePriceListCurr", derivePriceListCurr);
            elapsed += pEng.runStepFromFile(sdoA, "GetBaseListPrice", getBaseListPrice);
            //elapsed += pEng.runStepFromFile(sdoA, "ApplyPriceListAdjustment", applyPriceListAdjustment);
            //elapsed += pEng.runStepFromFile(sdoA, "GetListPrice", getListPrice); // rakesh
            if ( i>0 ) {
                total += elapsed;
                groovyTime.add(elapsed);
            }

            if ( i==repetitions-1 && AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "SDO Output: "+saveSDOAsString(sdoA), AppsLogger.FINEST);
            }
        }

        pEng.done();
        groovyAvg = total/(repetitions-1);

    /*
        // STS engine
        if ( runSTS ) {
            total = 0;
            for (int i=0; i<repetitions; i++) {
                DataObject sdoB = generateTestSDO(numOfLines);
                Map<String, Object> args = new HashMap<String, Object>();
                args.put("PriceRequest", sdoB);
    
                long start = System.currentTimeMillis();
                Executor.run("Get Base List Price for Timing", 1, args);
                long elapsed = System.currentTimeMillis()-start;
                if ( i>0 ) {
                    total += elapsed;
                    stsTime.add(elapsed);
                }
            }
            stsAvg = total/(repetitions-1);
        }

        // print results
        System.out.print("\tGroovy");
        if ( runSTS ) System.out.print("\tSTS");
        System.out.print('\n');
        for (int i=0; i<groovyTime.size(); i++) {
            System.out.print(i+1);
            System.out.print("\t"+groovyTime.get(i)+" ms.");
            if ( runSTS ) System.out.print("\t"+stsTime.get(i)+" ms.");
            System.out.print("\n");
        }
        System.out.println("-----------------------------");
        System.out.print("Avg\t"+groovyAvg+" ms.");
        if ( runSTS ) System.out.print("\t"+stsAvg+ " ms.");
        System.out.print('\n');
    */
    }

    @Test
    public void invokeProcess() {
        DataObject sdoA = generateTestSDO(1);

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Input:\n" + saveSDOAsString(sdoA), AppsLogger.FINEST);
        }

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("priceRequest", sdoA);
        args = Executor.run("QP_PRICE_SALES_TRANSACTION", args);

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Output:\n" + saveSDOAsString(sdoA), AppsLogger.FINEST);
        }

        Map<List, Map> ia = ((HashMap) ProcessController.getInstance().getGlobalInstance(Thread.currentThread().getId()).getProperty("itemAttribute"));
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            for (Map.Entry itemAttr : ia.entrySet()) {
                List a = (List) itemAttr.getKey();
                Map<String, Object> b = (Map) itemAttr.getValue();
                AppsLogger.write(this, a.toString(), AppsLogger.FINEST);
                for (Map.Entry<String, Object> attr : b.entrySet()) {
                    AppsLogger.write(this, attr.getKey()+" -> "+attr.getValue(), AppsLogger.FINEST);
                }
            }
        }
        Assert.assertEquals("Wrong number of item attributes", ia.size(), 1);

        try {
            Thread.sleep(100);
        }catch(Exception e) {
            System.out.println("****Exception: Thread is not sleeping !!****");
        }
    }

   //@Test
   public void runDummyProcess() {
        DataObject sdoA = generateTestSDO(1);
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("PriceRequest", sdoA);
        ProcessController ctrl = ProcessController.getInstance();

     //   Executor.initializeGlobalVars();   // it is defined private so this won't work

        Process pst = ctrl.getProcess("TESTDPLC");
        //Step basePrice = new Step("DerivePriceListCurrency");
        //pst.addTask(basePrice);
        pst.run(args);

        
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Output:\n" + saveSDOAsString(sdoA), AppsLogger.FINEST);
        }
    }

    //@Test
    public void submitMultiplePriceRequests() {
        int numOfLines = 1;
        int priceListId = 1002;

        long[] inventoryItemsIds_A = {149};
        long[] inventoryItemsIds_B = {137};
        PriceReq p1 = createPriceRequest(priceListId, inventoryItemsIds_A, numOfLines);
        PriceReq p2 = createPriceRequest(priceListId, inventoryItemsIds_B, numOfLines);
        PriceReq p3 = createPriceRequest(priceListId, inventoryItemsIds_A, numOfLines);

        Thread firstRequest = generatePriceRequestThread("firstRequest", p1);
        Thread secondRequest = generatePriceRequestThread("secondRequest", p2);
        Thread thirdRequest = generatePriceRequestThread("thirdRequest", p3);
        
        try {
            AppsLogger.write(this, "*******Waiting for Threads to finish******", AppsLogger.FINE);
            firstRequest.join();
            secondRequest.join();
            thirdRequest.join();
            
            // We should not need to sleep the main Thread but I kept it since I faced an error 
            // once while running the test in which the main thread finished before 'secondRequest' 
            // could finish its operations.
            Thread.sleep(5000);
        }
        catch (InterruptedException e) {
            AppsLogger.write(this, "-----------Price Request Test Thread Interrupted----------", AppsLogger.FINE);
        }

        printIsThreadAlive(firstRequest);
        printIsThreadAlive(secondRequest);
        printIsThreadAlive(thirdRequest);
        
        AppsLogger.write(this, "Exiting the Main Thread", AppsLogger.FINE);
    }

    private PriceReq createPriceRequest(int priceListId, long[] inventoryItemIds, int numOfLines) {        
        DataObject sdo = EngineTest.generateTestSDO(priceListId, inventoryItemIds, numOfLines);
        PriceReqParams p = new PriceReqParams(sdo);        
        return new PriceReq(p);        
    }

    private Thread generatePriceRequestThread(String threadName, PriceReq p) {
        Thread priceRequest = new Thread(p, threadName);        

        try {
            
            priceRequest.start();
            p.getParams().setThreadId(priceRequest.getId());
        
        }catch(Exception e) {
            AppsLogger.write(this, "'" + threadName + "' Thread creation got INTERRUPTED !!! ", AppsLogger.FINE);
        }

        printIsThreadAlive(priceRequest);

        return priceRequest;
    }

    private void printIsThreadAlive(Thread t) {
        AppsLogger.write(this, t + ": Is Alive ? " + t.isAlive(), AppsLogger.FINE);
    }

    /**
     * Test the Groovy class loader cache by parsing a one-line script and
     * loading the smae class referenced by binary name.
     */
    //@Test
    public void testClassLoaderCache() {
        String classPrefix = "oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine.script.";
        String className = "FooClass";
        String classExtension = ".class";
        String classFileName = classPrefix + className + classExtension;
        String stmt = "def foo = 0";

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "Parsing test Groovy statement once...", AppsLogger.FINEST);
        }

        GroovyClassLoader gcl = ScriptEngine.getInstance().getGroovyClassLoader();
        gcl.parseClass(stmt, classFileName);

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "Loading class...", AppsLogger.FINEST);
        }

        Class clazz = null;
        String classToLoad = null;
        try {
            classToLoad = classPrefix + className;
            clazz = gcl.loadClass(classToLoad);
        }
        catch (ClassNotFoundException e) {
            AppsLogger.write(this, "Error loading class!", AppsLogger.SEVERE);
            Assert.fail("GroovyClassLoader.loadClass(\"" + classToLoad + "\") failure");
        }

        Assert.assertNotNull(clazz);

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "Loaded " + clazz.getName(), AppsLogger.FINEST);
        }
    }
       
    //@Test
    @Deprecated
    public void submitMultiplePriceRequest() {
    //        int repetitions = 2; //minimum 2, because the first run is thrown out
    //        int numOfLines = 1;
    //        long total = 0;
    //
    //        /* Groovy engine */
    //        ScriptEngine pEng = ScriptEngine.getInstance();
    //        String pricingUtil = Global.relativePath + "PricingUtil.groovy";
    //        pEng.loadOrParseClass("PricingUtil", pricingUtil);
    //
    //        for (int i=0; i<repetitions; ++i) {
    //            if ( i==1 ) { // Generate multiple requests for the second repetition only
    //                /*
    //                 * Generating the SDO
    //                 */
    //
    //                int priceListId = 1002;
    //                long[] inventoryItemIds_A = {149};
    //                DataObject sdoA = EngineTest.generateTestSDO(priceListId, inventoryItemIds_A, numOfLines);
    //                PriceReqParams pA = new PriceReqParams(pEng, sdoA);
    //                String[] steps = {"BasePrice", "PriceListAdjustment", "ListPrice"};
    //                for (int j=0; j<steps.length; j++) {
    //                    pA.setStep(steps[j]);
    //                }
    //
    //                long[] inventoryItemIds_B = {137};
    //                DataObject sdoB = EngineTest.generateTestSDO(priceListId, inventoryItemIds_B, numOfLines);
    //                PriceReqParams pB = new PriceReqParams(pEng, sdoB);
    //                for (int j=0; j<steps.length; j++) {
    //                    pB.setStep(steps[j]);
    //                }
    //
    //                long[] inventoryItemIds_C = {149};
    //                DataObject sdoC = EngineTest.generateTestSDO(priceListId, inventoryItemIds_C, numOfLines);
    //                PriceReqParams pC = new PriceReqParams(pEng, sdoC);
    //                for (int j=0; j<steps.length; j++) {
    //                    pC.setStep(steps[j]);
    //                }
    //
    //                PriceReq p1 = new PriceReq(pA);
    //                PriceReq p2 = new PriceReq(pB);
    //                PriceReq p3 = new PriceReq(pC);
    //
    //                generateMultipleRequests(p1, p2, p3);
    //
    //                AppsLogger.write(this, Thread.currentThread() + ": SDO_A Output ##############\n"+ EngineTest.saveSDOAsString(pA.getSdo()), AppsLogger.FINEST);
    //                AppsLogger.write(this, Thread.currentThread() + ": SDO_B Output ##############\n"+ EngineTest.saveSDOAsString(pB.getSdo()), AppsLogger.FINEST);
    //                AppsLogger.write(this, Thread.currentThread() + ": SDO_C Output ##############\n"+ EngineTest.saveSDOAsString(pC.getSdo()), AppsLogger.FINEST);
    //            }
    //        }
    //
    //        pEng.done();
    //
    //        AppsLogger.write(this, "Exiting the Main Thread", AppsLogger.FINE);
    }

      
    @Deprecated
    private void generateMultipleRequests(PriceReq p1, PriceReq p2, PriceReq p3) {
        ThreadGroup tg = new ThreadGroup("PriceRequest");
        Thread firstRequest = new Thread(tg, p1, "FirstRequest");
        Thread secondRequest = new Thread(tg, p2, "SecondRequest");
        Thread thirdRequest = new Thread(tg, p3, "ThirdRequest");

        try {
            firstRequest.start();
            p1.getParams().setThreadId(firstRequest.getId());
            //Thread.sleep(10000L);
            secondRequest.start();
            p2.getParams().setThreadId(secondRequest.getId());
    //            thirdRequest.start();
    //            p3.getParams().setThreadId(thirdRequest.getId());
        }catch(Exception e) {
            AppsLogger.write(this, "-----------Price Request Test Thread Interrupted----------", AppsLogger.FINE);
        }

        AppsLogger.write(this, firstRequest + ": Is Alive ? " + firstRequest.isAlive(), AppsLogger.FINE);
        AppsLogger.write(this, secondRequest + ": Is Alive ? " + secondRequest.isAlive(), AppsLogger.FINE);
    //        AppsLogger.write(this, thirdRequest + ": Is Alive ? " + thirdRequest.isAlive(), AppsLogger.FINE);

        try {
            AppsLogger.write(this, "*******Waiting for Threads to finish******", AppsLogger.FINE);
            firstRequest.join();
            secondRequest.join();
    //            thirdRequest.join();
            
            // We should not need to sleep the main Thread but I kept it since I faced an error 
            // once while running the test in which the main thread finished before 'secondRequest' 
            // could finish its operations.
            Thread.sleep(5000);
        }
        catch (InterruptedException e) {
            AppsLogger.write(this, "-----------Price Request Test Thread Interrupted----------", AppsLogger.FINE);
        }

        AppsLogger.write(this, firstRequest + ": Is Alive ? " + firstRequest.isAlive(), AppsLogger.FINE);
        AppsLogger.write(this, secondRequest + ": Is Alive ? " + secondRequest.isAlive(), AppsLogger.FINE);
    //        AppsLogger.write(this, thirdRequest + ": Is Alive ? " + thirdRequest.isAlive(), AppsLogger.FINE);
    }

    @Deprecated
    private void writeSDOToDatabase(PriceReq p1, PriceReq p2, PriceReq p3) {
        //URL of Oracle database server
    //        String url = "jdbc:oracle:thin:@slcac736.us.oracle.com:1561:ems1040";
    //
    //        //properties for creating connection to Oracle database
    //        Properties props = new Properties();
    //        props.setProperty("user", "fusion");
    //        props.setProperty("password", "fusion");
        
      
        //creating connection to Oracle database using JDBC
    //        int result = 0;
        try {
    //            Connection conn = DriverManager.getConnection(url,props);
            
            PriceReq arr[] = {p1, p2, p3};            
            Long threadId = null;
            Long primaryKey = null;
    //            String sql = null;
    //            PreparedStatement pstmt = null;
            // Inserting SDO into the table
            ApplicationModule engineAM = ScriptEngine.getInstance().getAmHelper().getAm(ProcessController.ENGINE_AM_NAME, ProcessController.ENGINE_AM_CONFIG);
            ViewObjectImpl stepVO = (ViewObjectImpl) engineAM.findViewObject("StepExec");
            
            for (int i = 0; i < arr.length; i++) {
                PriceReq p = arr[i];
                threadId = p.getParams().getThreadId();
                if (threadId != null) {                    
                    primaryKey = System.currentTimeMillis() + threadId;
                    ClobDomain sdoClob = new ClobDomain(saveSDOAsString((DataObject)p.getParams().getArgs().get(PriceReqParams.SDO_KEY)));
                    //Clob sdoClob = new SerialClob(saveSDOAsString(p.getParams().getSdo()).toCharArray());
    //                    Clob c = (Clob) saveSDOAsString(p.getParams().getSdo()).;
                    // Use the method below to insert into temporary table directly
                    
                    
    //                    sql = "INSERT INTO QP_SDO_TMP " + "VALUES(?, ?, ?)";
                    //System.out.println("SQL generated -----------> : |" + sql + "|");
                    //creating PreparedStatement object to execute query
    //                    pstmt = conn.prepareStatement(sql);
    //                    pstmt.setLong(1, primaryKey);
    //                    pstmt.setLong(2, threadId);
    //                    pstmt.setC
    //                    result = pstmt.executeUpdate();

    // If we use a VO to insert the data, then use the method below

                    NameValuePairs nvp = new NameValuePairs();
                    nvp.setAttribute("StepVersionId", primaryKey);
                    nvp.setAttribute("StepId", threadId);
                    nvp.setAttribute("Version", 0);
                    nvp.setAttribute("StepLogic", sdoClob);
                    nvp.setAttribute("StatusCode", "PUBLISHED");
                    nvp.setAttribute("CreationDate", new Timestamp(25L));
                    nvp.setAttribute("CreatedBy", "AkshayTest");
                    nvp.setAttribute("LastUpdatedBy", "AkshayTest");
                    nvp.setAttribute("LastUpdateDate", new Timestamp(25L));
                    nvp.setAttribute("ObjectVersionNumber", 1);
                    stepVO.createAndInitRow(nvp);
                    Transaction t = engineAM.getTransaction();
                    int andSaveChangeSet = t.commitAndSaveChangeSet();
                }
            }                            
        }catch (Exception e) {
            System.out.println(e);            
        }
    }

/********** Methods for testing individual Groovy scripts **********/

    public static void runGroovyScript(String className, String filename, Map<String, Object> args, Global g) {
        String viewRoot = System.getenv("ADE_VIEW_ROOT");
        String filePath = "/fusionapps/scm/components/pricing/priceExecution/pricingProcesses/engine/src/oracle/apps/scm/pricing/priceExecution/pricingProcesses/script/";
        String pricingUtil = viewRoot + filePath + "PricingUtil.groovy";

        ScriptEngine engine = ScriptEngine.getInstance();

        if ( g==null ) {
            g = generateDefaultGlobalInstance();
        }
        args.put("global", g);

        // No longer needed: PricingUtil is loaded from ProcessController, which is instantiated by generateDefaultGlobalInstance()
        //engine.loadOrParseClassFromFile("PricingUtil", pricingUtil);

        engine.runGroovyScriptFromFile(className, viewRoot + filePath + filename, args);
    }

    public static Global generateDefaultGlobalInstance() {
        Global gb = Executor.initializeGlobalVars();

        // use this area to add global variables in unit testing before moving them to Executor.initializeGlobalVars()

        return gb;
    }

    //@Test
    public void testRetrieveReferenceFromFile() throws InterruptedException {
        String retrieveRefAttrs = "QpRetrieveReferenceAttributes.groovy";

        // initialize global variables
        Global gb = generateDefaultGlobalInstance();
        ((Set) ((Map) gb.getProperty("invalid")).get("lines")).add(103L); // line 103 is in error

        // create SDO with help from context service
        ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject pri = (DataObject) contextSvc.createConsumer("Sales", "PriceSalesTransaction");
        DataObject hdr = pri.createDataObject("Header");
        hdr.setLong("HeaderId", 1);
        hdr.setLong("CustomerId", 1006);
        hdr.set("PriceAsOf", new Timestamp(System.currentTimeMillis()));
        DataObject line = pri.createDataObject("Line");
        line.setLong("LineId", 101);
        line.setLong("HeaderId", 1);
        line.setLong("InventoryItemId", 149);
        line.setLong("InventoryOrganizationId", 204);
        line = pri.createDataObject("Line");
        line.setLong("LineId", 102);
        line.setLong("HeaderId", 1);
        line.setLong("InventoryItemId", 149);
        line.setLong("InventoryOrganizationId", 204);
        line = pri.createDataObject("Line");
        line.setLong("LineId", 103);
        line.setLong("HeaderId", 1);
        line.setLong("InventoryItemId", 137);
        line.setLong("InventoryOrganizationId", 204);

        // construct input arguments
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("sdo", new GroovyDataObject(pri));

        runGroovyScript("RetrieveReferenceAttributes", retrieveRefAttrs, args, gb);

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "Wrote " + ((Map) gb.getProperty("itemAttribute")).size() + " item attribute(s)", AppsLogger.FINEST);
            AppsLogger.write(this, "Wrote " + ((Map) gb.getProperty("partyAttribute")).size() + " party attribute(s)", AppsLogger.FINEST);
            AppsLogger.write(this, "Wrote " + ((Map) gb.getProperty("partyOrgProfile")).size() + " party organization profile attribute(s)", AppsLogger.FINEST);
            AppsLogger.write(this, "Wrote " + ((Map) gb.getProperty("partyPersonProfile")).size() + " party person profile attribute(s)", AppsLogger.FINEST);
        }

        Assert.assertEquals(((Map) gb.getProperty("itemAttribute")).size(), 1); // Two lines with the same inventory item only create one item attribute entry
        Assert.assertEquals(((Map) gb.getProperty("partyAttribute")).size(), 1);
        Assert.assertEquals(((Map) gb.getProperty("partyOrgProfile")).size(), 1);
        Assert.assertEquals(((Map) gb.getProperty("partyPersonProfile")).size(), 0);

        Thread.sleep(5000);
    }

    //@Test
    public void testBasePriceFromFile() throws InterruptedException {
        String getBaseListPrice = "QpGetBaseListPrice.groovy";

        // construct input arguments
        int numLines = 2;
        Map<String, Object> args = new HashMap<String, Object>();
        DataObject sdo = generateTestSDO(numLines);
        DataObject line = sdo.createDataObject("Line"); // invalid line with item that doesn't exist
        line.setLong("LineId", 101+numLines);
        line.setLong("HeaderId", 1);
        line.setLong("InventoryItemId", -9999L);
        line.setLong("InventoryOrganizationId", 204L);
        line.setLong("AppliedPriceListId", 1002L);
        args.put("sdo", new GroovyDataObject(sdo));
        args.put("priceElemCode", "BASE_LIST_PRICE");

        // initialize global variables
        Global gb = generateDefaultGlobalInstance();
        for (int i=0; i<numLines+1; i++) {
            Map internalLine = new HashMap<String, Object>();
            internalLine.put("pricingDate", new Timestamp(System.currentTimeMillis()));
            internalLine.put("appliedCurrencyCode", "USD");
            ((Map) gb.getProperty("line")).put(new Long(101+i), internalLine);
        }
        List itemKey = new ArrayList<Long>();
        itemKey.add(-9999L);
        itemKey.add(204L);
        Map itemAttr = new HashMap<String, Object>();
        itemAttr.put("itemNumber", "B06U5");
        ((Map) gb.getProperty("itemAttribute")).put(itemKey, itemAttr);

        runGroovyScript("GetBaseListPrice", getBaseListPrice, args, gb);

        Assert.assertTrue("Line 103 should be invalid!", ((Set) ((Map) gb.getProperty("invalid")).get("lines")).contains(103L));

        AppsLogger.write(this, "SDO Output:\n" + saveSDOAsString(sdo), AppsLogger.FINEST);
        Thread.sleep(10000L);
    }

    //@Test
    public void testSetInitialValuesFromFile() {
        Global gb = generateDefaultGlobalInstance();

        ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject pri = (DataObject) contextSvc.createConsumer("Sales", "PriceSalesTransaction");

        new PricingServiceParameter(pri);
        new Header(pri);
        new Line(pri);
        Line l2 = new Line(pri);
        l2.setLineId(102L);
        Line l3 = new Line(pri);
        l3.setLineId(103L);
        l3.setInventoryItemId(137L);

        // Converting the SDO to GroovyDataObject so that it can be updated in the script
        GroovyDataObject sdo = new GroovyDataObject(pri);

        // construct input arguments
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("sdo", sdo);

        // Run the script
        runGroovyScript("SetInitialValues", "QpSetInitialValues.groovy", args, gb);

        // Verify the values                
        Map globalHeaderRecords = (Map) gb.getProperty("header");
        Map globalLineRecords = (Map) gb.getProperty("line");
        Map globalPricingObjectParameterRecords = (Map) gb.getProperty("pricingObjectParameter");
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "Wrote " + globalHeaderRecords.size() + " Header Record(s) to global", AppsLogger.FINEST);
            AppsLogger.write(this, "Wrote " + globalLineRecords.size() + " Line Record(s) to global", AppsLogger.FINEST);
            AppsLogger.write(this, "Wrote " + globalPricingObjectParameterRecords.size() + " Pricing Object Parameter Record(s) to global", AppsLogger.FINEST);
        }

        Assert.assertEquals(1, globalHeaderRecords.size());
        Assert.assertEquals(3, globalLineRecords.size());
        Assert.assertEquals(10, globalPricingObjectParameterRecords.size());
    }

  //  @Test
    public void testFileDeriveCurr() throws InterruptedException {
        String retrieveRefAttrs = "QpRetrieveReferenceAttributes.groovy";
        String derivePLCurr     = "QpDerivePriceListCurrency.groovy";
        String setInitial       = "QpSetInitialValues.groovy";

        // initialize global variables
        Global gb = generateDefaultGlobalInstance();

        // create SDO with help from context service
        ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject pri = (DataObject) contextSvc.createConsumer("Sales", "PriceSalesTransaction");
        
        DataObject psp = pri.createDataObject("PricingServiceParameter");
        psp.setString("PricingContext", "SALES");
        psp.setString("OutputStatus", "SUCCESS");
        
        DataObject hdr = pri.createDataObject("Header");
        hdr.setLong("HeaderId", 1);
        hdr.setLong("SellingBusinessUnitId", 204);
        hdr.setLong("PricingStrategyId", 111);
        hdr.setLong("CustomerId", 1006);
        hdr.setString("OverrideCurrencyCode", "JPY");
        hdr.set("PriceAsOf", new Timestamp(System.currentTimeMillis()));

        DataObject line = pri.createDataObject("Line");
        line.setLong("LineId", 101);
        line.setLong("HeaderId", 1);
        line.setLong("InventoryItemId", 149);
        line.setLong("InventoryOrganizationId", 204);
        line.setLong("AppliedPriceListId", 1002);
        MeasureTypeImpl lineQty = (MeasureTypeImpl) line.createDataObject("LineQuantity");
        lineQty.setValue(new BigDecimal(2));
        lineQty.setUnitCode("Ea");
        line.setString("LineQuantityUOMCode", "Ea");
        line.setString("LineTypeCode", "BUY");
        line.set("PriceAsOf", new Timestamp(System.currentTimeMillis()));
        line.setString("OverrideCurrencyCode", "USD");
        line.setLong("PricingStrategyId", 111);
       // line.setLong("OverridePriceListId", 1002);   //RA
        
        DataObject term = pri.createDataObject("PricingTerm");
        term.setLong("ApplyToEntityId", 101);
        term.setLong("TermId", 888);
        term.setString("ApplyToEntityCode", "LINE");
     
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("sdo", new GroovyDataObject(pri));
       

        runGroovyScript("RetrieveReferenceAttributes", retrieveRefAttrs, args, gb);
        runGroovyScript("SetInitialValue", setInitial, args, gb);
        runGroovyScript("DerivePriceListCurrency", derivePLCurr, args, gb);
    
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "Wrote " + ((Map) gb.getProperty("partyPersonProfile")).size() + " party person profile attribute(s)", AppsLogger.FINEST);
            AppsLogger.write(this, "SDO Output: "+saveSDOAsString(pri), AppsLogger.FINEST);
        }
    }

    //@Test
    public void testRoundListPrice() {
        String setInitialValues = "QpSetInitialValues.groovy";
        String getBaseListPrice = "QpGetBaseListPrice.groovy";
        String getListPrice = "QpRunningUnitPrcChrgComp.groovy";

        // initialize global variables
        Global gb = generateDefaultGlobalInstance();
        //gb.addVariable("dummyLong", new Long((long)(Math.random() * 100)));

        // create SDO with help from context service
        ContextService<DataObject> contextSvc = ContextServiceFactory.getSDOServiceInstance();
        DataObject pri = generateTestSDO(1); //(DataObject) contextSvc.createConsumer("Sales", "PriceRequestInternal");

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Input:\n" + saveSDOAsString(pri), AppsLogger.FINEST);
        }

        // construct input arguments
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("sdo", new GroovyDataObject(pri));
        args.put("priceElemCode", "BASE_LIST_PRICE");
        args.put("priceElementCode", "LIST_PRICE");
        args.put("priceElementUsageCode", "LIST_PRICE");
        args.put("performRounding", true);

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Input:\n" + saveSDOAsString(pri), AppsLogger.FINEST);
        }        

        runGroovyScript("QpSetInitialValues", setInitialValues, args, gb);
        runGroovyScript("QpGetBaseListPrice", getBaseListPrice, args, gb);
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO 1:\n" + saveSDOAsString(pri), AppsLogger.FINEST);
        }
        runGroovyScript("QpRunningUnitPrcChrgComp", getListPrice, args, gb);
        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO 1:\n" + saveSDOAsString(pri), AppsLogger.FINEST);
        }

        try {
            Thread.sleep(1000);
        } catch (Exception e) {
        }
    }
    
    //@Test
    public void testFileGetCostListChargeComp() throws InterruptedException {
        String retrieveRefAttrs = "QpRetrieveReferenceAttributes.groovy";
        String derivePLCurr     = "QpDerivePriceListCurrency.groovy";
        String setInitial       = "QpSetInitialValues.groovy";
        String getBaseListPrice = "QpGetBaseListPrice.groovy";
        String getCostListChargeComp   = "QpGetCostListChargeComponent.groovy";

        // initialize global variables

        Global gb = generateDefaultGlobalInstance();
        // construct input arguments       
        Map<String, Object> args = new HashMap<String, Object>();
        DataObject sdo = generateTestSDO(1);
        args.put("sdo", new GroovyDataObject(sdo));
        args.put("priceElemCode", "BASE_LIST_PRICE");
        args.put("priceElementCode", "LIST_PRICE");
        args.put("priceElementUsageCode", "LIST_PRICE");
        args.put("costChargeId", 1L);
        

        // runGroovyScript("RetrieveReferenceAttributes", retrieveRefAttrs, args, gb);  // comment out this step from SetInitialValues else throws error
        runGroovyScript("SetInitialValues", setInitial, args, gb);
        runGroovyScript("DerivePriceListCurrency", derivePLCurr, args, gb);
        runGroovyScript("GetBaseListPrice", getBaseListPrice, args, gb);
        AppsLogger.write(this, "*******Running the Derive CL step******", AppsLogger.FINE);
        runGroovyScript("GetCostListChargeComp", getCostListChargeComp, args, gb);

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "SDO Output: "+saveSDOAsString(sdo), AppsLogger.FINEST);
        }
        
        Thread.sleep(5000L);
    }

    //@Test
    public void testTieredPricingFromFile() throws InterruptedException {
        // construct input arguments
        Map<String, Object> args = new HashMap<String, Object>();
        DataObject sdo = generateTestSDO(1);
        args.put("sdo", new GroovyDataObject(sdo));

        // initialize global variables
        Global gb = generateDefaultGlobalInstance();
        Map<String, Long> testTier = new HashMap<String, Long>();
        testTier.put("chargeId", 1L);
        testTier.put("tierHeaderId", 10010L);
        List<Map<String, Long>> testQueue = new ArrayList<Map<String, Long>>();
        testQueue.add(testTier);
        ((Map) gb.getProperty("tierQueue")).put("SEGMENT_PRICE", testQueue);

        runGroovyScript("QpSetInitialValues", "QpSetInitialValues.groovy", args, gb);
        args.put("priceElemCode", "BASE_LIST_PRICE");
        runGroovyScript("QpGetBaseListPrice", "QpGetBaseListPrice.groovy", args, gb);
        args.remove("priceElemCode");
        ((Map) ((Map) gb.getProperty("line")).get(101L)).put("appliedCurrencyCode", "EUR");
        args.put("phaseKey", "SEGMENT_PRICE");
        args.put("pricingObjectKey", "PRICE_LIST_TIER");
        runGroovyScript("QpApplyTieredPricing", "QpApplyTieredPricing.groovy", args, gb);

        AppsLogger.write(this, "SDO Output:\n" + saveSDOAsString(sdo), AppsLogger.FINEST);
        Thread.sleep(1000L);
    }
}
