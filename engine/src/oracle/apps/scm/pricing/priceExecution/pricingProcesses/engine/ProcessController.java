package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import groovy.lang.Script;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import oracle.apps.fnd.applcore.log.AppsLogger;

import oracle.jbo.Row;

public class ProcessController {
    /* temporary */
    private static final String VIEW_ROOT = System.getenv("ADE_VIEW_ROOT");
    private static final String FILE_PATH = "/fusionapps/scm/components/pricing/priceExecution/pricingProcesses/engine/src/oracle/apps/scm/pricing/priceExecution/pricingProcesses/script/";
    private static final String EXTENSION = ".groovy";

    protected static final String ENGINE_AM_CONFIG = "EngineAMShared";
    protected static final String ENGINE_AM_NAME = "oracle.apps.scm.pricing.priceExecution.pricingProcesses.publicModel.engine.applicationModule.EngineAM";

    private ScriptEngine engine;
    private ConcurrentMap<Long, Object> globalMap = new ConcurrentHashMap<Long, Object>();
    private Map<String, Process> processMap;
    private Map<String, Step> stepMap;

    private ProcessController() {
        engine = ScriptEngine.getInstance();
        processMap = new ConcurrentHashMap<String, Process>();
        stepMap = new ConcurrentHashMap<String, Step>();

        // FOR REVIEW: load "PricingUtil" Groovy class
        String pricingUtil = VIEW_ROOT + FILE_PATH + "PricingUtil" + EXTENSION;
        engine.loadOrParseClassFromFile("PricingUtil", pricingUtil);
    }

    private static class ProcessControllerHolder {
        private static final ProcessController INSTANCE = new ProcessController();
    }

    public static ProcessController getInstance() {
        return ProcessControllerHolder.INSTANCE;
    }

    /**
     * Returns a <code>Process</code> from the internal cache (map). If it's not
     * in cache, then read it from the DB and cache it.
     * 
     * The cached version is always the latest published version executed in
     * production.
     */
    public Process getProcess(String processKey) {        
        if ( processMap.containsKey(processKey) ) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " +  Thread.currentThread().getId() + ") Reading Process |" + processKey + "| from CACHE.", AppsLogger.FINEST);
            }
            return processMap.get(processKey);            
        }
        else {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " +  Thread.currentThread().getId() + ") Creating Process |" + processKey + "| from DB.", AppsLogger.FINEST);
            }
            return createAndCacheProcess(processKey);
        }
    }

    /**
     * Returns a <code>Step</code> from the internal cache (map). If it's not
     * in cache, then read it from the DB and cache it.
     * <p>
     * The cached version is always the latest published version executed in
     * production.
     */
    public Step getStep(String stepKey) {
        if ( stepMap.containsKey(stepKey) ) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " +  Thread.currentThread().getId() + ") Reading Step |" + stepKey + "| from CACHE", AppsLogger.FINEST);
            }
            return stepMap.get(stepKey);
        }
        else {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " +  Thread.currentThread().getId() + ") Creating Step |" + stepKey + "| from DB", AppsLogger.FINEST);
            }
            return createAndCacheStep(stepKey);
        }            
    }

    /**
     * Instantiates a new <code>Process</code>, loads its definition from the
     * database and stores it in the process cache (map).
     */
    private Process createAndCacheProcess(String processKey) {
        Process p;
        // when multiple threads call getProcess for the same process key, the
        // first thread needs an intrinsic lock on processMap to create and
        // cache a Process before the other threads can read it
        synchronized (processMap) {
            p = new Process(processKey);
            processMap.put(processKey, p);
        }
        return p;
    }

    /**
     * Instantiates a new <code>Step</code>, loads its definition from the
     * database and stores it in the step cache (map).
     */
    private Step createAndCacheStep(String stepKey) {        
        Step s = new Step(stepKey);                
        stepMap.put(stepKey, s);
        return s;
    }

    /**
     * Returns the <code>Script</code> for a given named step. This is currently
     * hard-coded to load a Groovy script from file instead of database, and
     * assumes the file name is the same as the step name.
     */
    public Script getScriptForStepFromFile(String stepName) {
        // This stepName comes from the database, but since we read the 
        // script from a file, I need to point it to correct file
        if ( "QP_GET_BASE_LIST_PRICE".equalsIgnoreCase(stepName) ) {
            stepName = "BasePrice";
        }
        else if ( "QP_GET_LIST_PRICE".equalsIgnoreCase(stepName) ) {
            stepName = "ListPrice";
        }
        else if ( "PRICE_LIST_ADJUSTMENT".equalsIgnoreCase(stepName) ) {
            stepName = "PriceListAdjustment";
        }
        String fullPath = VIEW_ROOT + FILE_PATH + stepName + EXTENSION;
        return engine.loadOrParseClassFromFile(stepName, fullPath);
    }

    /**
     * Returns the <code>Script</code> for a given named step.
     */
    public Script getScriptForStep(String stepName, String scriptContent) {
        return engine.loadOrParseClass(stepName, scriptContent);
    }

    // EXPERIMENTAL
    public boolean registerGlobalInstance(Global g) {
        return registerGlobalInstance(Thread.currentThread().getId(), g);
    }

    boolean registerGlobalInstance(long threadId, Global g) {
        boolean status = globalMap.putIfAbsent(threadId, g)==null;

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "Registered global instance for thread " + threadId, AppsLogger.FINEST);
        }

        return status;
    }

    // EXPERIMENTAL
    protected void unregisterGlobalInstance() {
        unregisterGlobalInstance(Thread.currentThread().getId());
    }

    protected void unregisterGlobalInstance(long threadId) {
        globalMap.remove(threadId);

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(this, "Unregistered global instance for thread " + threadId, AppsLogger.FINEST);
        }
    }

    protected Global getGlobalInstance(long threadId) {
        return (Global) globalMap.get(threadId);
    }

    /* deprecated methods */
    @Deprecated
    public boolean addProcess(String processKey, Process p) {
        if ( p!=null ) {
            processMap.put(processKey, p);
            return true;
        }
        else
            return false;
    }

    @Deprecated
    public boolean addStep(String stepKey, Step s) {
        if ( s!=null ) {
            stepMap.put(stepKey, s);
            return true;
        }
        else
            return false;
    }
}
