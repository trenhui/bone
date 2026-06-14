package com.bone.engine.extension.studio.domain.gateway;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import java.util.List;

/** 扩展点读侧端口（ADR-0013）。 */
public interface ExtPointReadPort {

  List<ExtPoint> findAll();

  List<ExtPoint> search(String keyword);

  long count();
}
