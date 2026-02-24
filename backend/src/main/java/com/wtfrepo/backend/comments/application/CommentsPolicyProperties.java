package com.wtfrepo.backend.comments.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Property-backed M05 comments policy snapshot.
 *
 * <p>These values stay local until admin policy publishing is introduced in a later module.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.comments.policy")
public class CommentsPolicyProperties {

  private int commentCostBug = 200;
  private int contentMaxLength = 4000;
  private int mentionMaxCount = 5;
  private int reportMessageMaxLength = 500;
  private int listDefaultLimit = 20;
  private int listMaxLimit = 50;
  private int chiefConclusionThreshold = 10;
  private String deletedPlaceholder = "此病历已封存";
}
