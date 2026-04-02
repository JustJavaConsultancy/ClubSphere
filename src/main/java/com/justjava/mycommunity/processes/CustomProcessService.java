package com.justjava.mycommunity.processes;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CustomProcessService {
    private final RuntimeService runtimeService;
    private final HistoryService historyService;

    public CustomProcessService(RuntimeService runtimeService, HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.historyService = historyService;
    }

    @Async
    public void startProcess(String procesKey, String businessKey,
                                        Map<String,Object> variables){

        System.out.println("Process Started");

        runtimeService.createProcessInstanceBuilder()
                .processDefinitionKey(procesKey)
                .businessKey(businessKey)
                .variables(variables)
                .start();
    }

    public ProcessInstance getSingleProcessInstance(String processId, String processDefKey){
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(processId)
                .processDefinitionKey(processDefKey)
                .includeProcessVariables()
                .singleResult();
    }

    public List<ProcessInstance> getAllProcessInstance(String processDefKey, String businessKey){
        return runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefKey)
                .processInstanceBusinessKey(businessKey)
                .includeProcessVariables()
                .active()
                .orderByProcessInstanceId().desc()
                .list();
    }

    public List<HistoricProcessInstance> getAllHistoricInstances(String processDefinitionKey, String businessKey){
        return historyService.createHistoricProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .processInstanceBusinessKey(businessKey)
                .finished()
                .includeProcessVariables()
                .list();
    }

    public List<HistoricVariableInstance> getSingleHistoricInstanceVar(String processInstanceId) {
        return historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();
    }

}
