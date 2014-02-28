package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.Script;

import java.math.BigDecimal;

import java.sql.Timestamp;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import oracle.apps.fnd.applcore.log.AppsLogger;

public class BaseScript extends Script {
    public static final String PRICING_PROCESS_AM_INST = "PricingProcessAMShared";
    public static final String PRICING_PROCESS_AM = "oracle.apps.scm.pricing.priceExecution.pricingProcesses.publicModel.applicationModule.PricingProcessAM";
    public static final String APP_SHORT_NAME = "QP";
    public static final String MESSAGE_TYPE_ERR = "ERROR";
    public static final String MESSAGE_TYPE_WARN = "WARNING";
    public static final String ENTITY_CODE_HEADER = "HEADER";
    public static final String ENTITY_CODE_LINE = "LINE";
    public static final String ENTITY_CODE_TERM = "PRICING TERM";
    
    private ScriptEngine engine = ScriptEngine.getInstance();

    public BaseScript(Binding binding) {
        super(binding);        
    }

    public BaseScript() {
        super();
    }
    
    public Object run() {
        return null;
    }

    public List queryVO(String amConfig, String amName, String voInstance, String vcName, Map<String, Object> bindVars) {
        return DataHelper.groovyQueryVO(amConfig, amName, voInstance, vcName, bindVars);
    }

    public void invokeStep(String stepName, Map<String, Object> bindVars) {
        engine.invokeStep(stepName, bindVars);
    }

    public boolean isInDateRange(java.util.Date start, java.util.Date end, Timestamp match) {
        if ( start!=null ) {
            if ( start instanceof java.sql.Date ) {
                start = new Timestamp(((java.sql.Date) start).getTime());
            }
            else if ( start instanceof Timestamp ) {
                // do nothing
            }
            else throw new IllegalArgumentException("Argument 'start' should be of type java.sql.Date or java.sql.Timestamp");
        }

        if ( end!=null ) {
            if ( end instanceof java.sql.Date ) {
                end = new Timestamp(((java.sql.Date) end).getTime());
            }
            else if ( end instanceof Timestamp ) {
                // do nothing
            }
            else throw new IllegalArgumentException("Argument 'end' should be of type java.sql.Date or java.sql.Timestamp");
        }

        return DataHelper.isInDateRange((Timestamp) start, (Timestamp) end, match);
    }

    public String getFndMessage (String appShortName, String messageName, Map<String, Object> bindVars) {
        return DataHelper.getFndMessage(appShortName, messageName, bindVars);
    }
    
    /**
     * Returns the default NLS language for the session
     */
    public String getDefaultLanguage() {
        return DataHelper.getDefaultLanguage();
    }

    public BigDecimal getNextId() {
        return oracle.jbo.server.uniqueid.UniqueIdHelper.getNextId();
    }

    public void finest(Object o) {
        writeLog(o, AppsLogger.FINEST);
    }

    public void finer(Object o) {
        writeLog(o, AppsLogger.FINER);
    }

    public void fine(Object o) {
        writeLog(o, AppsLogger.FINE);
    }

    public void severe(Object o) {
        writeLog(o, AppsLogger.SEVERE);
    }

    private void writeLog(Object o, Level logLevel) {
        if ( o!=null ) {
            if ( AppsLogger.isEnabled(logLevel) ) {
                String message;
                // this only handles Strings or closures
                if ( o instanceof Closure ) {
                    // assume the closure returns a string
                    message = (String) ((Closure) o).call();
                }
                else if ( o instanceof String ) {
                    message = (String) o;
                }
                else {
                    return;
                }
                AppsLogger.write(this, "(Thread " + Thread.currentThread().getId() + ") " + message, logLevel);
            }            
        }
    }

    public void debugInfo() {
        if ( AppsLogger.isEnabled(AppsLogger.SEVERE) ) {
            if ( this.getClass().getPackage()==null ) {
                AppsLogger.write(this.getClass(), "*** Null package, \"+Modifier.toString(this.getClass().getModifiers())+\" class \"+this.getClass().getName()+\" ***", AppsLogger.SEVERE);
            }
            else {
                AppsLogger.write(this.getClass(), "*** "+this.getClass().getPackage().getName()+"."+this.getClass().getName()+" ***", AppsLogger.SEVERE);
            }
        }
    }
}
