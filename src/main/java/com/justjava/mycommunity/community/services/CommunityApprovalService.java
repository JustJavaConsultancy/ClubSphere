package com.justjava.mycommunity.community.services;

import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.dto.ApprovalTaskDTO;
import com.justjava.mycommunity.userManagement.UserRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityApprovalService {

    private final TaskService taskService;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;

    // 🔹 1. Get Admin Tasks
    public List<ApprovalTaskDTO> getPendingTasks(String adminUserId) {

        List<Task> tasks = taskService.createTaskQuery()
                //.taskAssignee(adminUserId)
                .active()
                .list();
        System.out.println("This is the total amount of task: " + tasks.size());

        // 🔹 Extract IDs
        List<Long> communityIds = new ArrayList<>();
        List<String> userIds = new ArrayList<>();

        Map<String, Map<String, Object>> taskVarsMap = new HashMap<>();

        for (Task task : tasks) {
            Map<String, Object> vars = taskService.getVariables(task.getId());
            taskVarsMap.put(task.getId(), vars);

            Long communityId = (Long) vars.get("communityId");
            String userId = (String) vars.get("userId");

            if (communityId != null) communityIds.add(communityId);
            if (userId != null) userIds.add(userId);
        }

        // 🔹 Batch fetch communities
        Map<Long, Community> communityMap = communityRepository.findAllById(communityIds)
                .stream()
                .collect(Collectors.toMap(Community::getId, c -> c));

        // 🔹 Batch fetch users
        Map<String, User> userMap = userRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        // 🔹 Build DTO
        return tasks.stream().map(task -> {

            ApprovalTaskDTO dto = new ApprovalTaskDTO();
            dto.setTaskId(task.getId());
            dto.setTaskName(task.getName());

            Map<String, Object> vars = taskVarsMap.get(task.getId());

            String userId = (String) vars.get("userId");
            Long communityId = (Long) vars.get("communityId");

            dto.setUserId(userId);
            dto.setCommunityId(communityId);

            // 🔹 Community enrichment
            Community community = communityMap.get(communityId);
            if (community != null) {
                dto.setCommunityName(community.getName());
                dto.setCommunityDescription(community.getDescription());
                dto.setPrivate(community.isPrivate());
            }

            // 🔹 User enrichment (NEW)
            User user = userMap.get(userId);
            if (user != null) {
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
            }

            return dto;

        }).toList();
    }    // 🔹 2. Approve / Reject Task
    public void completeTask(String taskId, boolean approved) {

        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", approved);

        taskService.complete(taskId, variables);
    }
}