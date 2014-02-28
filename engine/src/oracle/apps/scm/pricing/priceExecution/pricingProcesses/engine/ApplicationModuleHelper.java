package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;

import oracle.jbo.ApplicationModule;
import oracle.jbo.ApplicationModuleHandle;
import oracle.jbo.client.Configuration;

public class ApplicationModuleHelper {
    private Map<String, ApplicationModuleHandle> amHandleMap = new ConcurrentHashMap<String, ApplicationModuleHandle>();
    private Map<String, ApplicationModule> amMap = new ConcurrentHashMap<String, ApplicationModule>();
    
    public ApplicationModuleHelper() {
    }
    
    private boolean isEmptyString(String inputStr) {
        if ( inputStr==null || inputStr.trim().length()==0 ) {
            return true;
        }
        else {
            return false;
        }
    }
    
    /*
    private String getAmKey(String amName, String amConfig) {
        if (isEmptyString(amName) || isEmptyString(amConfig)) {
            //throw new SetQueryException("ApplicationModuleName and ApplicationModuleConfig are required field. Name is %amName, and Config is %amConfig.");
        }
        return amName + "." + amConfig;
    }
    */

    /**
     * @param amName
     * @param amConfig
     * @return ApplicationModule
     */
    public ApplicationModule getAm(String amName, String amConfig) {
        String amKey = amName + "." + amConfig; //TODO: check for null values and throw exception
        boolean releaseAM = false;

        ApplicationModule am = amMap.get(amKey);
        if ( am!=null ) {
            return am;
        }

        ApplicationModuleHandle amHandle = amHandleMap.get(amKey);

        if ( amHandle!=null ) {
            am = amHandle.useApplicationModule();
            amMap.put(amKey, am);
            return am;
        }

        long begintimestamp = System.currentTimeMillis();
        try {
            synchronized(ApplicationModuleHelper.class) {
                amHandle = Configuration.createRootApplicationModuleHandle(amName, amConfig);
            }

            amHandleMap.put(amKey, amHandle);
            am = amHandle.useApplicationModule();

            //am.processChangeNotifications();

            amMap.put(amKey, am);
            return am;
        }
        finally {
            if ( releaseAM && amHandle!=null ) {
                Configuration.releaseRootApplicationModuleHandle(amHandle, false);
                amHandleMap.remove(amKey);
                amMap.remove(amKey);
            }
        }
    }

    public void releaseAllAM() {
        for (ApplicationModuleHandle amHandle : amHandleMap.values()) {
            if ( amHandle!=null ) {
                Configuration.releaseRootApplicationModuleHandle(amHandle, false);
            } 
        }
        amMap.clear();
        amHandleMap.clear();
    }
}
