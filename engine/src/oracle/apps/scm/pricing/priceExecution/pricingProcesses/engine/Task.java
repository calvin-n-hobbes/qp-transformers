package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import java.math.BigDecimal;

import java.sql.Date;
import java.sql.Timestamp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.apps.fnd.applcore.log.AppsLogger;

import oracle.jbo.Row;

public class Task {
    // parameter data types
    private static final String PARAM_STRING_TYPE = "ORA_STRING";
    private static final String PARAM_DECIMAL_TYPE = "ORA_DECIMAL";
    private static final String PARAM_DATE_TYPE = "ORA_DATE";
    private static final String PARAM_TIMESTAMP_TYPE = "ORA_TIMESTAMP";

    // parameter reference types
    private static final String PROCESS_REF_TYPE_CODE = "ORA_PROCESS";
    private static final String STEP_REF_TYPE_CODE = "ORA_STEP";

    // VO constants
    private static final String PROC_STEP_PARAM_VAL_EXEC_VO_INSTANCE_NAME = "ProcessStepParamValueExec";
    private static final String PROC_STEP_PARAM_VALUE_VC = "GetProcessStepParamValues";

    private Long processStepId = null;
    private Activity activity = null;
    private Map<String, ParameterValue> parameterValues = new HashMap<String, ParameterValue>();

    private boolean active = true;
    private String taskName = null;
    private Timestamp startDate = null, endDate = null;

    private class ParameterValue {
        private String mapping;
        private Object constantValue;

        ParameterValue(String s, Object o) {
            super();
            mapping = s;
            constantValue = o;
        }
    }

    /**
     *
     * @param id the process step ID, needed only to retrieve parameter values
     * @param type either 'PROCESS' or 'STEP'
     * @param key the process or step code
     */
    public Task(Long id, String type, String key) {
        super();

        processStepId = id;
        if ( PROCESS_REF_TYPE_CODE.equalsIgnoreCase(type) ) {
            // Get the process based on activity key (e.g., ProcessCode)
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " + Thread.currentThread().getId() + ") Calling getProcess for |" + key + "| ", AppsLogger.FINEST);
            }
            activity = ProcessController.getInstance().getProcess(key);
        }
        else if ( STEP_REF_TYPE_CODE.equalsIgnoreCase(type) ) {
            // Get the process based on task key (e.g., StepCode)
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " + Thread.currentThread().getId() + ") Calling getStep for |" + key + "| ", AppsLogger.FINEST);
            }
            activity = ProcessController.getInstance().getStep(key);
        }

        populateParameterValues();
    }

    /**
     * Loads all mappings and values for process step parameters into the
     * <code>parameterValues</code> map. A parameter can be assigned either a
     * constant value or a value mapped to a calling process's parameter.
     * <p>
     * When running a task, the underlying activity's argument list, or binding
     * context, is constructed by assigning each parameter to the corresponding
     * value stored in <code>parameterValues</code>.
     */
    public void populateParameterValues() {
        // Query for process step parameter values
        Map<String, Object> paramValBindVars = new HashMap<String, Object>();
        paramValBindVars.put("processStepId", processStepId);
        List<Row> paramValues = DataHelper.queryVO(ProcessController.ENGINE_AM_CONFIG, ProcessController.ENGINE_AM_NAME, PROC_STEP_PARAM_VAL_EXEC_VO_INSTANCE_NAME, PROC_STEP_PARAM_VALUE_VC, paramValBindVars);

        // Populate each parameter value with a mapping or a constant value
        for (Row paramValue : paramValues) {
            if ( paramValue.getAttribute("ProcessStepParameterCode")!= null ) {
                String paramCode = (String) paramValue.getAttribute("ProcessStepParameterCode");
                //String paramTypeCode = (String) paramValue.getAttribute("ProcessStepParameterTypeCode");
                String mapping = (String) paramValue.getAttribute("ProcessParameterCode");
                //String procParamTypeCode = (String) paramValue.getAttribute("ProcessParameterTypeCode");

                // if parameter is mapped to a constant, then assume its value exists in one of the four data type
                // fields, even though the parameter's data type is unknown
                Object paramVal = null;
                if ( mapping==null ) {
                    paramVal = (String) paramValue.getAttribute("ParameterValueString");
                    if ( paramVal==null ) {
                        paramVal = (BigDecimal) paramValue.getAttribute("ParameterValueNumber");
                        if ( paramVal==null ) {
                            paramVal = (Date) paramValue.getAttribute("ParameterValueDate");
                            if ( paramVal==null ) {
                                paramVal = (Timestamp) paramValue.getAttribute("ParameterValueTimestamp");
                                if ( paramVal==null ) {
                                    String val = (String) (paramValue.getAttribute("ParameterValueString"));
                                    paramVal = new Boolean(val.toLowerCase());
                                }
                            }
                        }
                    }
                }

                parameterValues.put(paramCode, new ParameterValue(mapping, paramVal));
            }
        }
    }

    /**
     * Sets up parameters, assigning values where necessary, and runs the
     * underlying activity.
     * 
     * @param bindingContext
     */
    public void run(Map<String, Object> bindingContext) {
        // assign values to parameters and create binding
        Map<String, Object> binding = new HashMap<String, Object>();

        for (Map.Entry<String, Activity.Parameter> param : activity.getParameters().entrySet()) {
            String paramName = param.getKey();
            Activity.Parameter paramDef = param.getValue();
            
            // retrieve value and assign to 'Input' parameters
            if ( paramDef.isInputType() ) {
                // parameter values come from a process parameter or a constant
                // if from a process parameter, then look up value from binding context,
                // otherwise use the constant stored in the map
                Object paramValue = null;
                ParameterValue valueDef = parameterValues.get(paramName);
                if ( valueDef==null ) {
                    // TODO: check this case
                    paramValue = paramDef.getDefaultValue();
                }
                else if ( valueDef.mapping!=null ) {
                    // beware this could be null...
                    paramValue = bindingContext.get(valueDef.mapping);
                }
                else if ( valueDef.constantValue!=null ) {
                    paramValue = valueDef.constantValue;
                }
                else {
                    // this is a problem if the parameter value contains neither mapping nor constant!
                }
                binding.put(paramName, paramValue);
            }
            // assign default values to 'Output' or 'None' parameters
            else if ( paramDef.isOutputType() || paramDef.isNoneType() ) {
                binding.put(paramName, paramDef.getDefaultValue());
            }
        }
        
        // run activity
        activity.run(binding);

        // assign back any output parameters
        for (Map.Entry<String, Activity.Parameter> param : activity.getParameters().entrySet()) {
            String paramName = param.getKey();
            Activity.Parameter paramDef = param.getValue();

            if ( paramDef.isOutputType() ) {
                // write updated values from the binding to original locations in bindingContext
                ParameterValue valueDef = parameterValues.get(paramName);
                if ( valueDef.mapping!=null ) {
                    bindingContext.put(valueDef.mapping, binding.get(paramName));
                }                
            }
        }
    }

    /**
     * Copy values of output parameters from an activity's bindings back to the
     * parent binding context according to the mappings stored in the activity's
     * parameter value definition.
     * 
     * @param from a binding map
     * @param to the original binding context map
     */
    public void assignOutputs(Map<String, Object> from, Map<String, Object> to) {
        for (Map.Entry<String, Activity.Parameter> param : activity.getParameters().entrySet()) {
            String paramName = param.getKey();
            Activity.Parameter paramDef = param.getValue();

            if ( paramDef.isOutputType() ) {
                String mapping = parameterValues.get(paramName).mapping;
                if ( mapping!=null ) {
                    to.put(mapping, from.get(mapping));
                }
            }
        }
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public String getName() { return taskName; }
    public void setName(String s) { taskName = s; }

    public Timestamp getStartDate() { return startDate; }
    public void setStartDate(Timestamp t) { startDate = t; }

    public Timestamp getEndDate() { return endDate; }
    public void setEndDate(Timestamp t) { endDate = t; }

    public boolean isCurrentlyValid() {
        java.util.Date date = new java.util.Date();
        return DataHelper.isInDateRange(startDate, endDate, new Timestamp(date.getTime()));
    }

    public boolean isProcess() {
        return (activity instanceof Process);
    }

    public boolean isStep() {
        return (activity instanceof Step);
    }

    public String getActivityCode() {
        if ( activity instanceof Process ) {
            return ((Process) activity).getProcessCode();
        }
        else if ( activity instanceof Step ) {
            return ((Step) activity).getStepCode();
        }
        else {
            return null;
        }
    }

    public Map<String, Activity.Parameter> getParameters() {
        return activity.getParameters();
    }
}
