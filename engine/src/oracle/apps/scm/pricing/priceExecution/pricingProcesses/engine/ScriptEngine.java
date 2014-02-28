package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import commonj.sdo.DataObject;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import oracle.apps.fnd.applcore.log.AppsLogger;

import oracle.jbo.ApplicationModule;
import oracle.jbo.Row;
import oracle.jbo.ViewCriteria;
import oracle.jbo.server.ViewObjectImpl;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;

/*
 * <code>ScriptEngine</code> is a lazy-initialized, thread-safe singleton class
 * that holds references to instances of <code>GroovyClassLoader</code> and
 * <code>ApplicationModuleHandle</code>.
 */
public class ScriptEngine {

    private static final String CLASS_PREFIX = ScriptEngine.class.getPackage().getName() + ".";
    private static final String CLASS_SUFFIX = ".class";

    private GroovyClassLoader gcl;
    private ApplicationModuleHelper amHelper;    
    private int counter = 1;

    private ScriptEngine() {
        gcl = getDefaultClassLoader();
        amHelper = new ApplicationModuleHelper();
    }

    protected ApplicationModuleHelper getAmHelper() {
        return amHelper;
    }

    // lazy initiation holder class
    private static class ScriptEngineHolder {
        private final static ScriptEngine INSTANCE = new ScriptEngine();
    }

    public static ScriptEngine getInstance() {
        return ScriptEngineHolder.INSTANCE;
    }

    public GroovyClassLoader getGroovyClassLoader() {
        return gcl;
    }

    private GroovyClassLoader getDefaultClassLoader() {
        // create class loader with base class for enabling Pricing-flavored methods
        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass(BaseScript.class.getName());
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        //ClassLoader parent = getClass().getClassLoader();
        return new GroovyClassLoader(parent, config);
    }

    // call this to initialize the AM and other control structures
    protected void init() {
        // may not be needed anymore?
    }
    
    // call this when all Groovy scripts are finished
    protected void done() {
        amHelper.releaseAllAM();
    }

    @Deprecated
    public Script loadClass(String stepName, String filePath) {
        return loadOrParseClassFromFile(stepName, filePath);
    }

    /*
     * Returns the <code>Script</code> object associated with a Groovy script
     * file.
     * Reads the file content and passes it to <code>loadOrParseClass<code>
     * method to get the script
     */      
    public Script loadOrParseClassFromFile(String stepName, String filePath) {
        String fullClassName = stepName;

        StringBuilder sb = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            
            String line;
            while ( (line = br.readLine()) != null ) {
                sb.append(line).append('\n');
            }

            br.close();
        }
        catch (FileNotFoundException fnfe) {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(this, "!! File "+filePath+" not found !!", AppsLogger.SEVERE);
            }
        }
        catch (IOException ioe) {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(this, "!! I/O exception !!", AppsLogger.SEVERE);
            }
        }
        
        return loadOrParseClass(fullClassName, sb.toString());
    }

    /* Scripts are compiled into classes that can be retrieved from the
     * class loader cache. If the class cannot be loaded (e.g., not in cache),
     * it must be parsed and placed in cache for future use.
     * 
     * Note that <code>parseClass</code> takes the binary name of the class with
     * a filename extension (e.g., ".class", but "." by itself will suffice)
     * while <code>loadClass</code> takes the binary name without an extension.
     */
    public Script loadOrParseClass(String className, String scriptContent) {
        Class clazz = null;
        Script cachedScript = null;
        className = CLASS_PREFIX + className;
        String fullClassFileName = className + CLASS_SUFFIX;
        long threadId = Thread.currentThread().getId();

        try {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "* Loading or parsing " + className + "...", AppsLogger.FINEST);
            }
            clazz = gcl.loadClass(className);
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "* (Thread " + threadId + ") Class "+clazz.getSimpleName()+" loaded from class cache", AppsLogger.FINEST);
            }
        }
        catch (ClassNotFoundException e) {
            try {
                clazz = gcl.parseClass(scriptContent, fullClassFileName);
                // place in class loader cache
                gcl.loadClass(className);
            }
            catch (CompilationFailedException cfe) {
                // TODO: how should we handle Groovy compilation errors?
                if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                    AppsLogger.write(this, "!! Error compiling " + className + " !!", AppsLogger.SEVERE);
                }
                return null;
            }
            catch (ClassNotFoundException cnfe) {
                if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                    AppsLogger.write(this, "!! Could not load " + className + " immediately after parsing it !!", AppsLogger.SEVERE);
                }
            }
        }

        try {
            cachedScript = (Script) clazz.newInstance();
        }
        catch (InstantiationException ie) {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(this, "!!!! (Thread " + threadId + ") InstantiationException !!!!", AppsLogger.SEVERE);
            }
        }
        catch (IllegalAccessException iae) {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(this, "!!!! (Thread " + threadId + ") InstantiationException !!!!", AppsLogger.SEVERE);
            }
        }
        catch (ClassCastException cce) {
            // pure Groovy classes are not scripts, and cannot be cast to groovy.lang.Script
            // we can ignore this exception
            if ( AppsLogger.isEnabled(AppsLogger.FINE) ) {
                AppsLogger.write(this, "!!!! (Thread " + threadId + ") Warning: "+cce.getMessage(), AppsLogger.FINE);
            }
        }
        
        return cachedScript;
    }

    public void runGroovyScriptFromFile(String className, String filePath, Map<String, Object> bindingContext) {
        Script s = loadOrParseClassFromFile(className, filePath);
        if ( s!=null ) {
            Binding binding = new Binding();
            synchronized (binding) {
                for (Map.Entry<String, Object> arg : bindingContext.entrySet()) {
                    binding.setVariable(arg.getKey(), arg.getValue());
                }
                s.setBinding(binding);
                s.run();
            }
        }
        else {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(this, "!!!! Unable to run step because of Groovy script compilation errors !!!!", AppsLogger.SEVERE);
            }
        }
    }

    @Deprecated
    public long runStepFromFile(DataObject sdo, String stepName, String filePath) {
        Script step = null;
        long startTime = 0, endTime = 0;
        long threadId = Thread.currentThread().getId();

        step = loadOrParseClassFromFile(stepName, filePath);

        if ( step!=null ) {
            Binding binding = new Binding();
            synchronized (binding) {
                binding.setVariable("sdo", new GroovyDataObject(sdo));
                binding.setVariable("global", ProcessController.getInstance().getGlobalInstance(Thread.currentThread().getId()));
                step.setBinding(binding);
                startTime = System.currentTimeMillis();
                step.run();
                endTime = System.currentTimeMillis();
            }
        }
        else {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(this, "!!!! (Thread " + threadId + ") Unable to run step because of Groovy script compilation errors !!!!", AppsLogger.SEVERE);
            }
        }

        return endTime-startTime;
    }

    @Deprecated
    public long run(DataObject sdo, String script) {
        Class clazz = null;
        long startTime = 0, endTime = 0;
        long threadId = Thread.currentThread().getId();

        /* Groovy class loader should cache scripts based on some unique "name" key, e.g., "hello1234"
         * 
         * Once we formally implement and map keys to scripts, we can call loadClass() to get "cached" classes and extract the scripts.
         * If loadClass() fails because it's the first time seeing this key, then we know to call parseClass() instead.
         */
        try {
            String s = ScriptEngine.class.getPackage().getName()+".Hello"+(counter++)+".class";
            clazz = getGroovyClassLoader().parseClass(script, s);
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(ScriptEngine.class, "(Thread " + threadId + ") Parsed class "+clazz.getSimpleName(), AppsLogger.FINEST);
            }
            Script step = (Script) clazz.newInstance();
            
            Binding binding = new Binding();
            binding.setVariable("sdo", new GroovyDataObject(sdo));
            binding.setVariable("elapsedSlice", new Long(0));
            
            step.setBinding(binding);
            synchronized (ScriptEngine.class) {
                startTime = System.currentTimeMillis();
                step.run();
                endTime = System.currentTimeMillis();
            }
            if ( !binding.getVariable("elapsedSlice").equals(new Long(0)) ) {
                return ((Long) binding.getVariable("elapsedSlice")).longValue();
            }
        } catch (InstantiationException ie) {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(ScriptEngine.class, "!! (Thread " + threadId + ") Instantiation exception !!", AppsLogger.SEVERE);
            }
        } catch (IllegalAccessException iae) {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(ScriptEngine.class, "!! (Thread " + threadId + ") Illegal access exception !!", AppsLogger.SEVERE);
            }
        }
        
        return endTime-startTime;
    }
    
    @Deprecated
    public long runFromFile(DataObject sdo, String filePath) {
        StringBuilder sb = new StringBuilder();
        
        try {
            BufferedReader br = new BufferedReader(new FileReader(filePath));
            
            String line = br.readLine();
            while ( line != null ) {
                sb.append(line).append('\n');
                line = br.readLine();
            }
            
            br.close();
        } catch (FileNotFoundException e) {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(ScriptEngine.class, "!! File "+filePath+" not found !!", AppsLogger.SEVERE);
            }
        } catch (IOException e) {
            if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
                AppsLogger.write(ScriptEngine.class, "!! I/O exception !!", AppsLogger.SEVERE);
            }
        }        

        return run(sdo, sb.toString());
    }

    // wrapper method
    public void invokeStep(String stepName, Map<String, Object> bindVars) {
        long threadId = Thread.currentThread().getId();

        Step s = ProcessController.getInstance().getStep(stepName);
        if ( s!=null ) {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " + threadId + ") invokeStep: executing step '"+stepName+"'", AppsLogger.FINEST);
            }
            // assign values to parameters
            Map<String, Object> binding = s.createBinding(bindVars);
            /* temporary */
            if ( binding.size()==0 ) binding = bindVars;
            s.run(binding);

            // assign back any output parameters
            for (String paramName : s.getOutputParameterNames()) {
                // write updated values from the binding to original locations in 'bindVars'
                bindVars.put(paramName, binding.get(paramName));
            }
        }
        else if ( AppsLogger.isEnabled(AppsLogger.FINER) ) {
            AppsLogger.write(this, "(Thread " + threadId + ") invokeStep: error finding step '"+stepName+"'!", AppsLogger.FINER);
        }
    }

    @Deprecated
    protected Script getScript(String stepName) {
        if ( "Global".equals(stepName) )
            return null;
        else {
            String viewRoot = System.getenv("ADE_VIEW_ROOT");
            String filePath = "/fusionapps/scm/components/pricing/priceExecution/pricingProcesses/engine/src/oracle/apps/scm/pricing/priceExecution/pricingProcesses/script/";
            String extension = ".groovy";
            return loadOrParseClass(stepName, viewRoot + filePath + stepName + extension);
        }
    }
}
