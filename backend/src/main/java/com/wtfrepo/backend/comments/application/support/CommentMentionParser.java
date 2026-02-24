package com.wtfrepo.backend.comments.application.support;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Extracts @mention username tokens from comment markdown content.
 *
 * <p>MVP parser keeps implementation lightweight and deterministic:
 *
 * <ul>
 *   <li>Matches token format: {@code @username} where username length is 1..64.
 *   <li>Preserves first-appearance order while deduplicating repeated mentions.
 *   <li>Does not build full markdown AST in this stage.
 * </ul>
 */
@Component
public class CommentMentionParser {

  private static final Pattern MENTION_PATTERN =
      Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_-]{1,64})");

  /**
   * Returns unique mention usernames in first-seen order.
   *
   * <p>An empty set is returned when no mention token is found.
   */
  public Set<String> extractMentionedUsernames(String contentMd) {
    LinkedHashSet<String> usernames = new LinkedHashSet<>();
    Matcher matcher = MENTION_PATTERN.matcher(contentMd);
    while (matcher.find()) {
      usernames.add(matcher.group(1));
    }
    return usernames;
  }
}

