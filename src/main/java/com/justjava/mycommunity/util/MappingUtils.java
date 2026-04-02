package com.justjava.mycommunity.util;

import com.justjava.mycommunity.chat.dto.ChatGroupRequestDTO;
import com.justjava.mycommunity.chat.dto.CommentDTO;
import com.justjava.mycommunity.chat.dto.PostDTO;
import com.justjava.mycommunity.chat.dto.SessionDTO;
import com.justjava.mycommunity.chat.entity.User;
import com.justjava.mycommunity.event.Event;
import com.justjava.mycommunity.network.ChatGroupRequest;
import com.justjava.mycommunity.posts.Post;
import com.justjava.mycommunity.userManagement.UserDTO;
import com.justjava.mycommunity.userManagement.UserGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MappingUtils {

    public static List<UserDTO> mapUsersToDTO(Collection<User> users){
        List<UserDTO> dtos = new ArrayList<>();
        for (User user : users) {
            UserDTO userDTO = new UserDTO();
            userDTO.setUserId(user.getUserId());
            userDTO.setFirstName(user.getFirstName());
            userDTO.setLastName(user.getLastName());
            userDTO.setEmail(user.getEmail());
            userDTO.setLevel(user.getLevel());
            userDTO.setStatus(user.getStatus());
            userDTO.setPrivacy(user.getPrivacy());
            userDTO.setGroup(user.getUserGroup() != null? user.getUserGroup().stream()
                    .map(UserGroup::getGroupName).collect(Collectors.joining(", ")): "");
            userDTO.setAvatar(user.getAvatar());
            dtos.add(userDTO);
        }
        return dtos;
    }

    public static List<ChatGroupRequestDTO> mapChatRequestToDTO(Collection<ChatGroupRequest> chatGroupRequests) {
        List<ChatGroupRequestDTO> dtos = new ArrayList<>();
        for (ChatGroupRequest chatGroupRequest : chatGroupRequests) {
            ChatGroupRequestDTO dto = new ChatGroupRequestDTO();
            dto.setId(chatGroupRequest.getId());
            dto.setFullName(chatGroupRequest.getUser().getFullName());
            dto.setChatGroupName(chatGroupRequest.getChatGroup().getName());
            dto.setStatus(chatGroupRequest.getStatus());
            dto.setGroupId(chatGroupRequest.getChatGroup().getId());
            dtos.add(dto);
        }
        return dtos;
    }

    public static List<PostDTO> mapPosts(Collection<Post> posts) {
        List<PostDTO> postDTOs = new ArrayList<>();
        for (Post post : posts) {
            List<CommentDTO> comments = new ArrayList<>();
            PostDTO dto = new PostDTO();
            dto.setPostID(post.getId());
            dto.setContent(post.getContent());
            dto.setUserFullName(post.getUser().getFullName());
            dto.setUserEmail(post.getUser().getEmail());
            dto.setPicture(post.getPicture());
            dto.setDateCreated(String.valueOf(post.getDateCreated()));
            dto.setPrivacy(post.isPrivacy());
            post.getComments().forEach(comment -> {
                CommentDTO commentDTO = new CommentDTO();
                commentDTO.setUserId(comment.getUser().getUserId());
                commentDTO.setComment(comment.getContent());
                commentDTO.setSenderFullName(comment.getUser().getFullName());
                commentDTO.setDateCreated(String.valueOf(comment.getDateCreated()));
                comments.add(commentDTO);

            });
            dto.setComments(comments);
            postDTOs.add(dto);
        }
        return postDTOs;
    }

    public static List<SessionDTO> mapEventsToDTO(Collection<Event> events){
        List<SessionDTO> sessionDTOS = new ArrayList<>();
        for (Event event : events) {
            SessionDTO dto = new SessionDTO();
            dto.setId(event.getId());
            dto.setDescription(event.getDescription());
            dto.setStartDate(String.valueOf(event.getStartDate()));
            dto.setStartTime(String.valueOf(event.getStartTime()));
            dto.setDuration(event.getDuration());
            dto.setNumberOfParticipants(event.getParticipants() != null ? event.getParticipants().size() : 0);
            dto.setStatus();
            dto.setHasCertificate(event.isHasCertificate());
            dto.setCertificateHtml(event.getCertificateHtml());
            dto.setSessionType(event.getSessionType());
            dto.setVideoLink(event.getVideoLink());

            if (event.getModule() != null) {
                dto.setModuleName(event.getModule().getName());
            } else {
                dto.setModuleName(event.getTitle());
                List<String> participantNames = event.getParticipants().stream()
                        .map(p -> p.getUser().getFullName())
                        .toList();
                dto.setParticipantNames(participantNames);
            }
            sessionDTOS.add(dto);
            System.out.println("Mapped session/meeting: " + dto.getModuleName() + " with status: " + dto.getStatus() + " on date: " + dto.getStartDate()+" certificate: "+dto.isHasCertificate());
        }
        return sessionDTOS;
    }

}
