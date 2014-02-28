package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import java.math.BigDecimal;

import java.sql.Date;
import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.jbo.Row;

public abstract class Activity {
    // activity param data types
    private static final String PARAM_STRING_TYPE = "ORA_STRING";
    private static final String PARAM_DECIMAL_TYPE = "ORA_DECIMAL";
    private static final String PARAM_DATE_TYPE = "ORA_DATE";
    private static final String PARAM_TIMESTAMP_TYPE = "ORA_TIMESTAMP";
    private static final String PARAM_BOOLEAN_TYPE = "ORA_BOOLEAN";

    // activity param types
    private static final String PARAM_INPUT_TYPE = "ORA_INPUT";
    private static final String PARAM_OUTPUT_TYPE = "ORA_OUTPUT";
    private static final String PARAM_IO_TYPE = "ORA_INPUT_OUTPUT";
    private static final String PARAM_NONE_TYPE = "ORA_NONE";

    // VO constants
    private static final String PROCESS_PARAM_EXEC_VO_INSTANCE_NAME = "ProcessParameterExec";
    private static final String STEP_PARAM_EXEC_VO_INSTANCE_NAME = "StepParameterExec";
    private static final String PROC_STEP_PARAM_VAL_EXEC_VO_INSTANCE_NAME = "ProcessStepParamValueExec";
    private static final String PROC_PARAM_VC = "GetParameters";
    private static final String STEP_PARAM_VC = "GetStepParameters";
    private static final String PROC_STEP_PARAM_VALUE_VC = "GetProcessStepParamValues";

    // inner class
    public class Parameter {
        private String dataType;
        private String parameterType;
        private Object defaultValue;

        public Parameter(String dt, String pt, Object dv) {
            super();
            setDataType(dt);
            setParameterType(pt);
            setDefaultValue(dv);
        }

        public String getDataType() { return dataType; }
        public void setDataType(String d) { dataType = d; }
        public String getParameterType() { return parameterType; }
        public void setParameterType(String p) { parameterType = p; }
        public Object getDefaultValue() { return defaultValue; }
        public void setDefaultValue(Object o) { defaultValue = o; }

        boolean isInputType() {
            return (PARAM_INPUT_TYPE.equals(parameterType) || PARAM_IO_TYPE.equals(parameterType));
        }

        boolean isOutputType() {
            return (PARAM_OUTPUT_TYPE.equals(parameterType) || PARAM_IO_TYPE.equals(parameterType));
        }

        boolean isNoneType() {
            return (PARAM_NONE_TYPE.equals(parameterType));
        }
    }
    
    /* fields */
    private String name = null;
    private Map<String, Parameter> parameters = new HashMap<String, Parameter>();
    
    /* non-abstract methods */
    public void setName(String s) {
        name = s;
    }

    public String getName() {
        return name;
    }

    public Map<String, Activity.Parameter> getParameters() {
        return parameters;
    }

    /**
     *
     * @param activityId
     */
    public void populateParameters(Long activityId) {
        // Query for process parameters
        Map<String, Object> paramBindVars = new HashMap<String, Object>();
        List<Row> paramRows = null;
        if ( this instanceof Process ) {
            paramBindVars.put("processId", activityId);
            paramRows = DataHelper.queryVO(ProcessController.ENGINE_AM_CONFIG, ProcessController.ENGINE_AM_NAME, PROCESS_PARAM_EXEC_VO_INSTANCE_NAME, PROC_PARAM_VC, paramBindVars);
        }
        else if ( this instanceof Step ) {
            paramBindVars.put("stepId", activityId);
            paramRows = DataHelper.queryVO(ProcessController.ENGINE_AM_CONFIG, ProcessController.ENGINE_AM_NAME, STEP_PARAM_EXEC_VO_INSTANCE_NAME, STEP_PARAM_VC, paramBindVars);
        }

        // Populate each parameter
        for (Row paramRow : paramRows) {
            if ( paramRow.getAttribute("ParameterCode")!=null ) {
                String paramCode = (String) paramRow.getAttribute("ParameterCode");
                String paramType = (String) paramRow.getAttribute("ParameterTypeCode");
                String dataType = (String) paramRow.getAttribute("DatatypeCode");
                Object defaultValue = null;
                if ( PARAM_STRING_TYPE.equals(dataType) ) {
                    defaultValue = (String) paramRow.getAttribute("DefaultValueString");
                }
                else if ( PARAM_DECIMAL_TYPE.equals(dataType) ) {
                    defaultValue = (BigDecimal) paramRow.getAttribute("DefaultValueNumber");
                }
                else if ( PARAM_DATE_TYPE.equals(dataType) ) {
                    defaultValue = (Date) paramRow.getAttribute("DefaultValueDate");
                }
                else if ( PARAM_TIMESTAMP_TYPE.equals(dataType) ) {
                    defaultValue = (Timestamp) paramRow.getAttribute("DefaultValueTimestamp");
                }
                else if ( PARAM_BOOLEAN_TYPE.equals(dataType) ) {
                    defaultValue = new Boolean(paramRow.getAttribute("DefaultValueString").toString().toLowerCase());
                }

                parameters.put(paramCode, new Parameter(dataType, paramType, defaultValue));
            }
        }
    }

    /**
     * Constructs a map of the activity's parameters and assigns each a value
     * based on the parameter type. Input and 'None' parameters are assigned
     * their corresponding value from <code>bindingContext</code>, or a default
     * value if does not exist in <code>bindingContext</code>. Output parameters
     * are created in the binding context but do not have assigned values.
     * 
     * @param bindingContext the map of the calling activity's parameters and values;
     * the source of truth for assigning the activity's input and 'None' parameter values
     * @return the constructed binding map
     */
    public Map<String, Object> createBinding(Map<String, Object> bindingContext) {
        Map<String, Object> binding = new HashMap<String, Object>();

        for (Map.Entry<String, Activity.Parameter> param : parameters.entrySet()) {
            String paramName = param.getKey();
            Activity.Parameter paramDef = param.getValue();
            
            // retrieve value and assign to 'Input' and 'Input/Output' and 'None' parameters
            if ( paramDef.isInputType() || paramDef.isNoneType() ) {
                // parameter values come from a process parameter or a constant;
                // if from a process parameter, then look up value from 'args',
                // otherwise use the constant stored in the map
                Object paramValue = (bindingContext.containsKey(paramName)) ? bindingContext.get(paramName) : paramDef.defaultValue;
                binding.put(paramName, paramValue);
            }
            // leave 'Output' parameters null; don't assign default value yet
            else if ( paramDef.isOutputType() ) {
                binding.put(paramName, null);
            }
        }

        return binding;
    }

    public List<String> getOutputParameterNames() {
        List<String> outputParams = new ArrayList<String>();

        for (Map.Entry<String, Activity.Parameter> param : parameters.entrySet()) {
            if ( param.getValue().isOutputType() ) {
                outputParams.add(param.getKey());
            }
        }
        
        return outputParams;
    }

    /* abstract methods */
    public abstract void run(Map<String, Object> args);
}
