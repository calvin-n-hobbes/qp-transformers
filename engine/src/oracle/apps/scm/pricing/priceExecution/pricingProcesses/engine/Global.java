package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import groovy.lang.GroovyObjectSupport;
import java.util.HashMap;
import java.util.Map;

public class Global extends GroovyObjectSupport {
    private final long threadId;

    // These are global variables defined temporarily
    protected static final String viewRoot = System.getenv("ADE_VIEW_ROOT");
    protected static final String filePath = "/fusionapps/scm/components/pricing/priceExecution/pricingProcesses/engine/src/oracle/apps/scm/pricing/priceExecution/pricingProcesses/script/";
    protected static final String relativePath = viewRoot + filePath;

    private Map<String, Object> contents;

    public Global() {        
        threadId = Thread.currentThread().getId();           
        //System.out.println("threadId generated for new instance of Global class: " + threadId);
        contents = new HashMap<String, Object>();
    }

    public void addVariable(String name, Object var) {
        if ( !contents.containsKey(name) ) {
            contents.put(name, var);
        }
    }

    public void removeVariable(String name) {
        if ( contents.containsKey(name) ) {
            contents.remove(name);
        }
    }

    public Object getProperty(String property) {
        if ( contents.containsKey(property) ) {
            return contents.get(property);
        }
        else if ( "threadId".equals(property) ) {
            return threadId;
        }
        return null;
    }

    public void setProperty(String property, Object value) {
        if ( contents.containsKey(property) ) {
            contents.put(property, value);
            // TODO: how to deal with setting attributes that have not been added to global?
        }
        else if ( "threadId".equals(property) ) {
            // Do nothing, we do not want to change the threadid} 
        }
    }
}
