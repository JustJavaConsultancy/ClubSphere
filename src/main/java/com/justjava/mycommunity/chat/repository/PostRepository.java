package com.justjava.mycommunity.chat.repository;

import com.justjava.mycommunity.posts.Post;
import com.justjava.mycommunity.posts.PostLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByDateCreatedDesc();

    List<Post> findAllByPrivacyOrderByDateCreatedDesc(boolean privacy);

    @Query("SELECT p FROM Post p WHERE p.privacy = false OR (p.privacy = true AND p.user.userId = ?1) ORDER BY p.dateCreated DESC")
    List<Post> findPostsForUser(String userId);

    List<Post> findAllByPostLevelAndPostLevelIdOrderByDateCreatedDesc(PostLevel postLevel, Long postLevelId);

    List<Post> findAllByPostLevelOrderByDateCreatedDesc(PostLevel postLevel);
}
