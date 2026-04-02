package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.posts.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
}