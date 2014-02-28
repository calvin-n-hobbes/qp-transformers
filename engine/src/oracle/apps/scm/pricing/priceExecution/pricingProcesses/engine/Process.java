package oracle.apps.scm.pricing.priceExecution.pricingProcesses.engine;

import java.sql.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.apps.fnd.applcore.log.AppsLogger;

import oracle.jbo.Row;

public class Process extends Activity {    
    private static final String PROCESS_EXEC_VO_INSTANCE_NAME = "ProcessExec";
    private static final String PROCESS_STEP_EXEC_VO_INSTANCE_NAME = "ProcessStepExec";
    private static final String GET_PROCESS_VC = "GetProcessVersion";
    private static final String GET_PROCESS_STEP_VC = "GetProcessStepVersion";

    private Long processId;
    private String processCode;
    private String processTypeCode;
    private Long procVersionId;
    private List<Task> tasks;

    public Process(String processKey) {
        super();
        processCode = processKey;

        // 1.   Query the database for Process row, then assign those values to process attributes
        Map<String, Object> processBindVars = new HashMap<String, Object>();
        processBindVars.put("processCode", processCode);
        processBindVars.put("version", 0);
        List<Row> rows = null;
        synchronized (Process.class) {
            rows = DataHelper.queryVO(ProcessController.ENGINE_AM_CONFIG, ProcessController.ENGINE_AM_NAME, PROCESS_EXEC_VO_INSTANCE_NAME, GET_PROCESS_VC, processBindVars);
        }
        if ( !rows.isEmpty() ) {
            // The query should return only one record
            if ( rows.size()==1 ) {
                Row record = (Row) rows.get(0);
                processId = (Long) record.getAttribute("ProcessId");
                processTypeCode = (String) record.getAttribute("ProcessTypeCode");

                // query the task parameters
                if ( processId!=null ) {
                    populateParameters(processId);
                }
            }
        }

        tasks = new ArrayList<Task>();
        Map<String, Object> processStepBindVars = new HashMap<String, Object>();
        processStepBindVars.put("processCode", processCode);
        processStepBindVars.put("procVersion", 0);

        List<Row> processSteps = null;
        synchronized (Process.class) {
            processSteps = DataHelper.queryVO(ProcessController.ENGINE_AM_CONFIG, ProcessController.ENGINE_AM_NAME, PROCESS_STEP_EXEC_VO_INSTANCE_NAME, GET_PROCESS_STEP_VC, processStepBindVars);
        }

        for (Row record : processSteps) {
            Long processStepId = (Long) record.getAttribute("ProcessStepId");
            String refTypeCode = (String) record.getAttribute("ProcessStepRefTypeCode");
            String activityKey = (String) record.getAttribute("ProcessStepCode");

            Task task = new Task(processStepId, refTypeCode, activityKey);
            if ( task!=null ) {
                task.setStartDate((Timestamp) record.getAttribute("StartDate"));
                task.setEndDate((Timestamp) record.getAttribute("EndDate"));
                tasks.add(task);
            }
        }
    }

    /**
     *
     * @param bindingContext the map of process parameters and their values
     */
    public void run(Map<String, Object> bindingContext) {
        for (Task task : tasks) {
            if ( task.isActive() && task.isCurrentlyValid() ) {
                // print debug message distinguishing process from step invocation
                if ( AppsLogger.isEnabled(AppsLogger.FINEST) ) {
                    if ( task.isProcess() ) {
                        AppsLogger.write(this, "(Thread "+Thread.currentThread().getId()+") Running process "+task.getActivityCode(), AppsLogger.FINEST);
                    }
                    else if ( task.isStep() ) {
                        AppsLogger.write(this, "(Thread "+Thread.currentThread().getId()+") Running step "+task.getActivityCode(), AppsLogger.FINEST);
                    }
                }

                // construct bindings
                Map<String, Object> binding = createBinding(bindingContext);

                // run task
                task.run(binding);

                // copy output values from the binding to their mapped locations
                // in the parent binding context
                task.assignOutputs(binding, bindingContext);
            }
        }
    }

    protected String getProcessCode() {
        return processCode;
    }
}
