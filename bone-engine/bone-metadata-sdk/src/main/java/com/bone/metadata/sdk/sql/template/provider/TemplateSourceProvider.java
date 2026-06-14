package com.bone.metadata.sdk.sql.template.provider;

import com.bone.metadata.sdk.domain.exception.TemplateLoadException;
import com.bone.metadata.sdk.sql.template.TemplateDescriptor;
import java.lang.reflect.Method;
import java.net.URI;

public interface TemplateSourceProvider {
  boolean supports(URI sourceUri);

  LoadedSource load(Method method, TemplateDescriptor descriptor) throws TemplateLoadException;

  class LoadedSource {
    private final String rawContent;
    private final String charset;
    private final String origin;
    private final long fetchedAt;

    public LoadedSource(String rawContent, String charset, String origin, long fetchedAt) {
      this.rawContent = rawContent;
      this.charset = charset;
      this.origin = origin;
      this.fetchedAt = fetchedAt;
    }

    public String getRawContent() {
      return rawContent;
    }

    public String getCharset() {
      return charset;
    }

    public String getOrigin() {
      return origin;
    }

    public long getFetchedAt() {
      return fetchedAt;
    }
  }
}
