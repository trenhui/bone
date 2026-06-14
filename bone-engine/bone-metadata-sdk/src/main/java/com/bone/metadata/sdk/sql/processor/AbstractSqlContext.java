package com.bone.metadata.sdk.sql.processor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for SQL processing contexts that provides common functionality for SQL template
 * processing, reducing code duplication between different SQL processors.
 *
 * @param <T> the type of closure used by the processor
 */
public abstract class AbstractSqlContext<T> {
  protected final StringBuilder sql = new StringBuilder();
  protected final StringBuilder currentTagContent = new StringBuilder();
  protected final Map<String, Object> params;
  protected final Deque<T> closures = new ArrayDeque<>();
  protected boolean isProcessingTagContent = false;

  /**
   * Constructor with parameters.
   *
   * @param params the parameters map, may be null
   */
  protected AbstractSqlContext(Map<String, Object> params) {
    this.params = new HashMap<>();
    if (params != null) {
      // Filter out null values
      params.forEach(
          (key, value) -> {
            if (value != null) {
              this.params.put(key, value);
            }
          });
    }
  }

  /**
   * Appends text to the current content buffer based on whether we're processing a tag
   *
   * @param text the text to append
   */
  public void append(String text) {
    if (isProcessingTagContent) {
      currentTagContent.append(text);
    } else {
      sql.append(text);
    }
  }

  /** Starts processing tag content, resets the tag content buffer */
  public void startTagContent() {
    isProcessingTagContent = true;
    currentTagContent.setLength(0);
  }

  /**
   * Finishes processing tag content and returns the collected content
   *
   * @return the tag content
   */
  public String finishTagContent() {
    isProcessingTagContent = false;
    String content = currentTagContent.toString();
    currentTagContent.setLength(0);
    return content;
  }

  /**
   * Adds a parameter to the context, filtering out null values
   *
   * @param name parameter name
   * @param value parameter value
   */
  public void addParam(String name, Object value) {
    if (value != null) {
      params.put(name, value);
    }
  }

  /**
   * Pushes a closure onto the stack
   *
   * @param closure the closure to push
   */
  public void pushClosure(T closure) {
    closures.push(closure);
  }

  /**
   * Gets the SQL being built
   *
   * @return the SQL string
   */
  public String getSql() {
    return sql.toString();
  }

  /**
   * Gets the parameters map
   *
   * @return the parameters
   */
  public Map<String, Object> getParams() {
    return params;
  }
}
