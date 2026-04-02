package com.justjava.mycommunity.task;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service
public class TaskService {

    @Autowired
    private RepositoryService repositoryService;

    private final org.flowable.engine.TaskService flowableTaskService;
    private final HistoryService historyService;

    public TaskService(
                       org.flowable.engine.TaskService taskService, org.flowable.engine.TaskService flowableTaskService,
                       HistoryService historyService) {

        this.flowableTaskService = flowableTaskService;
        this.historyService = historyService;
    }

    public void completeTask(String taskId, Map<String,Object> variables) {
        flowableTaskService.complete(taskId,variables);
    }
    public List<org.flowable.task.api.Task> findActiveflowableTasks() {
        return flowableTaskService
                .createTaskQuery()
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();
    }
    public List<org.flowable.task.api.Task> findActiveflowableTasksByProcess(String processKey) {
        return flowableTaskService
                .createTaskQuery()
                .processDefinitionKey(processKey)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .list();
    }
    public org.flowable.task.api.Task findTaskById(String taskId) {
        return flowableTaskService
                .createTaskQuery()
                .active()
                .includeTaskLocalVariables()
                .includeProcessVariables()
                .taskId(taskId)
                .singleResult();

    }

    //new addition
    public org.flowable.task.api.Task getTaskByInstanceAndDefinitionKey(String processInstanceId, String taskDefinitionKey){
        return flowableTaskService.
                createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .includeProcessVariables()
                .singleResult();
    }

    public List<org.flowable.task.api.Task> getTaskByAssigneeAndProcessDefinitionKey
            (String assignee,String processDefinitionKey) {
        return flowableTaskService
                .createTaskQuery()
                .taskAssignee(assignee)
                .processDefinitionKey(processDefinitionKey)
                .includeProcessVariables()
                .list();
    }

    public List<HistoricTaskInstance> getCompletedTaskByAssigneeAndVariable(String assignee, String processKey,
                                                                            String variableName, String variableValue){
        return historyService
                .createHistoricTaskInstanceQuery()
                .processDefinitionKey(processKey)
                .taskAssignee(assignee)
                .processVariableValueEquals(variableName, variableValue)
                .finished()
                .list();
    }

    public List<HistoricTaskInstance> getCompletedTaskByAssignee(String assignee, String processKey){
        return historyService
                .createHistoricTaskInstanceQuery()
                .processDefinitionKey(processKey)
                .taskAssignee(assignee)
                .finished()
                .list();
    }


    public String getTaskDocumentation(String taskId) {
        org.flowable.task.api.Task task=flowableTaskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new RuntimeException("Task not found.");
        }
        String processDefinitionId = task.getProcessDefinitionId();
        String taskDefinitionKey = task.getTaskDefinitionKey();

        // 2) Load BPMN model
        BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
        org.flowable.bpmn.model.Process process = model.getMainProcess();

        // 3) Find UserTask definition
        FlowElement element = process.getFlowElement(taskDefinitionKey);

        if (element instanceof UserTask) {
            UserTask userTask = (UserTask) element;

            // 4) Retrieve documentation
            return userTask.getDocumentation();
        }
        return "Empty Documentation";
    }
}
