package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import commonj.sdo.DataObject;

import groovy.lang.Binding;
import groovy.lang.Script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.apps.fnd.applcore.log.AppsLogger;

import oracle.jbo.Row;

import oracle.jbo.domain.ClobDomain;

public class Step extends Activity {
    private static final String STEP_EXEC_VO_INSTANCE_NAME = "StepExec";
    private static final String GET_STEP_VC = "GetStep";

    private Long stepId;
    private String stepCode;
    private Script script;

    public Step() {
        super();
    }

    public Step(String stepKey) {
        stepCode = stepKey;

        // 1.   Query the database for Step row, and then assign those values to step attributes
        Map<String, Object> bindVars = new HashMap<String, Object>();
        bindVars.put("stepCode", stepKey);
        bindVars.put("version", 0);
        // Setting taskName since it is helpful in debugging
        setName(stepKey);
        List<Row> rows = null;
        synchronized (Step.class) {
            rows = DataHelper.queryVO(ProcessController.ENGINE_AM_CONFIG, ProcessController.ENGINE_AM_NAME, STEP_EXEC_VO_INSTANCE_NAME, GET_STEP_VC, bindVars);
        }
        // If we find the script in database, then use it to run the logic
        if ( !rows.isEmpty() ) {
            ClobDomain stepLogicClob = null;
            if ( !rows.isEmpty() ) {
                // The query should return only one record
                if ( rows.size()==1 ) {
                    Row record = (Row) rows.get(0);
                    stepId = (Long) record.getAttribute("StepId");
                    stepLogicClob = (ClobDomain) record.getAttribute("StepLogic");
                    // query step parameters
                    populateParameters(stepId);
                }
            }

            String stepLogicStr = stepLogicClob.toString();
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " +  Thread.currentThread().getId() + ") Got script |" + stepCode + "| from DB", AppsLogger.FINEST);
            }
            script = ProcessController.getInstance().getScriptForStep(stepCode, stepLogicStr);
        }
        // If we do not find the script in database, then take the script from a local file
        else {
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " +  Thread.currentThread().getId() + ") Got script |" + stepCode + "| from FILE. (Is this what you wanted?)", AppsLogger.FINEST);
            }
            script = ProcessController.getInstance().getScriptForStepFromFile(stepKey);
        }
    }

    /**
     * Runs the script
     */
    public void run(Map<String, Object> bindingContext) {
        Binding binding = new Binding();

        synchronized (Step.class) {
            binding.setVariable("global", ProcessController.getInstance().getGlobalInstance(Thread.currentThread().getId()));
            //set binding variables
            for (Map.Entry<String, Object> e : bindingContext.entrySet()) {
                String paramCode = e.getKey();
                Object paramVal = e.getValue();
                // cast DataObject to GroovyDataObject
                if ( paramVal!=null && paramVal instanceof DataObject ) {
                    paramVal = new GroovyDataObject((DataObject) paramVal);
                }
                binding.setVariable(paramCode, paramVal);
            }

            script.setBinding(binding);            
            if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                AppsLogger.write(this, "(Thread " +  Thread.currentThread().getId() + ") Calling script.run for |" + stepCode + "|", AppsLogger.FINEST);
            }
            script.run();

            // write output variables from binding back to binding context
            for (String outputParamCode : getOutputParameterNames()) {
                if ( binding.hasVariable(outputParamCode) ) {
                    Object paramVal = binding.getVariable(outputParamCode);
                    if ( paramVal instanceof GroovyDataObject ) {
                        paramVal = ((GroovyDataObject) paramVal).getDataObject();
                    }
                    bindingContext.put(outputParamCode, paramVal);
                }
            }
        }
    }

    /**
     * Converts name to a code by converting to all upper-case letters and
     * replacing white spaces with underscores.
     */
    private String getCodeFromName(String name) {
        return name.toUpperCase().replaceAll("\\s", "_");
    }

    protected String getStepCode() {
        return stepCode;
    }
}
