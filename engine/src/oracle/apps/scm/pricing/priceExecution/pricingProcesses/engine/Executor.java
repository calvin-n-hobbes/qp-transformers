package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import java.math.BigDecimal;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import java.util.Set;

import oracle.apps.fnd.applcore.log.AppsLogger;

public class Executor {
    private Executor() {
        super();
    }
    
    /**
     * Runs a process with given arguments.
     * TODO: change signature to allow for test mode and version summary.
     */
    public static Map<String, Object> run(String processCode, Map<String, Object> args) {
        ProcessController ctrl = ProcessController.getInstance();

        initializeGlobalVars();

        Activity process = ctrl.getProcess(processCode);
        if ( process!=null ) {
            // assign values to parameters
            Map<String, Object> binding = process.createBinding(args);

            process.run(binding);

            // assign back any output parameters
            for (String paramName : process.getOutputParameterNames()) {
                // write updated values from the binding to original locations in 'args'
                args.put(paramName, binding.get(paramName));
            }
        }
        
        // TODO: unregister global instance, ctrl.unregisterGlobalInstance();

        return args;
    }

    public static Global initializeGlobalVars() {
        long threadId = Thread.currentThread().getId();

        if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
            AppsLogger.write(Executor.class, "Registering Global instance for thread " + threadId + "...", AppsLogger.FINEST);
        }
        Global gb = new Global();
        if ( !ProcessController.getInstance().registerGlobalInstance(gb) && AppsLogger.isEnabled(AppsLogger.FINER) ) {
            AppsLogger.write(Executor.class, "Unable to register Global instance for thread " + threadId + "!", AppsLogger.FINER);
            return null;
        }

        // add test global variables
        gb.addVariable("chargeIdCounter", new Long(1));
        gb.addVariable("chargeComponentIdCounter", new Long(1));

        gb.addVariable("header", new HashMap<Long, Map<String, Object>>());
        gb.addVariable("line", new HashMap<Long, Map<String, Object>>());
        gb.addVariable("charge", new HashMap<Long, Map<String, Object>>());
        gb.addVariable("chargeComponent", new HashMap<Long, Map<String, Object>>());

        Map invalid = new HashMap<String, Set<Long>>();
        invalid.put("headers", new HashSet<Long>()); // global.invalid.headers is a set containing IDs of all headers in error
        invalid.put("lines", new HashSet<Long>()); // global.invalid.lines is a set containing IDs of all lines in error
        gb.addVariable("invalid", invalid);

        gb.addVariable("itemAttribute", new HashMap<List<Long>, Map<String, Object>>()); // [inventory item ID, inventory org ID] -> {item number, service duration type, BOM item type}
        gb.addVariable("partyAttribute", new HashMap<Long, Map<String, String>>()); // party ID -> {party name, party type}
        gb.addVariable("partyOrgProfile", new HashMap<List, Map<String, String>>()); // party ID -> {GSA indicator flag, line of business}
        gb.addVariable("partyPersonProfile", new HashMap<List, Map<String, String>>()); // party ID -> {gender, marital status}
        gb.addVariable("pricingObjectParameter", new HashMap<String, Map<String, String>>());        
        gb.addVariable("tierQueue", new HashMap<String, List<Map<String, Long>>>()); // phase key -> [{tiered pricing header ID, charge ID}]

        return gb;
    }
}
