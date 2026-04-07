package com.justjava.mycommunity.community.services;


import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.chat.repository.CommunityRepository;
import com.justjava.mycommunity.community.Community;
import com.justjava.mycommunity.community.dto.ApprovalTaskDTO;
import com.justjava.mycommunity.community.dto.CommunityDTO;
import com.justjava.mycommunity.userManagement.UserRepository;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityApprovalService {

    private final TaskService taskService;
    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;

    public List<ApprovalTaskDTO> getPendingTasks(String adminUserId) {
        List<Task> tasks = taskService.createTaskQuery()
                .active()
                .list();

        List<Long> communityIds = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        Map<String, Map<String, Object>> taskVarsMap = new HashMap<>();

        for (Task task : tasks) {
            Map<String, Object> vars = taskService.getVariables(task.getId());
            taskVarsMap.put(task.getId(), vars);

            Long communityId = (Long) vars.get("communityId");
            String userId = (String) vars.get("userId");

            if (communityId != null) {
                communityIds.add(communityId);
            }
            if (userId != null) {
                userIds.add(userId);
            }
        }

        Map<Long, CommunityDTO> communityMap = communityRepository.findAllById(communityIds)
                .stream()
                .collect(Collectors.toMap(
                        Community::getId,
                        c -> {
                            CommunityDTO dto = new CommunityDTO();
                            dto.setId(c.getId());
                            dto.setName(c.getName());
                            dto.setDescription(c.getDescription());
                            dto.setCommunityPrivacy(c.getCommunityPrivacy());
                            dto.setIsPrivate(c.isPrivate());
                            dto.setChannelId(c.getChannel() != null ? c.getChannel().getId() : null);
                            dto.setTownHallId(c.getTownHall() != null ? c.getTownHall().getId() : null);
                            dto.setOrganizationId(c.getOrganization() != null ? c.getOrganization().getId() : null);
                            return dto;
                        }
                ));

        Map<String, User> userMap = userRepository.findByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(User::getUserId, u -> u));

        return tasks.stream().map(task -> {
            ApprovalTaskDTO dto = new ApprovalTaskDTO();
            dto.setTaskId(task.getId());
            dto.setTaskName(task.getName());

            Map<String, Object> vars = taskVarsMap.get(task.getId());
            String userId = (String) vars.get("userId");
            Long communityId = (Long) vars.get("communityId");

            dto.setUserId(userId);
            dto.setCommunityId(communityId);

            CommunityDTO community = communityMap.get(communityId);
            if (community != null) {
                dto.setCommunityName(community.getName());
                dto.setCommunityDescription(community.getDescription());
                dto.setPrivate(community.getIsPrivate());
            }

            User user = userMap.get(userId);
            if (user != null) {
                dto.setFirstName(user.getFirstName());
                dto.setLastName(user.getLastName());
            }

            return dto;
        }).toList();
    }

    public void completeTask(String taskId, boolean approved) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("approved", approved);
        taskService.complete(taskId, variables);
    }
}