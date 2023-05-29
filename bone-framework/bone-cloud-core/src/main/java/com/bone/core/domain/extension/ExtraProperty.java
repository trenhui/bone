package com.bone.core.domain.extension;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author renhui.trh
 */
public class ExtraProperty extends ConcurrentHashMap<String, Object> implements Serializable {
    public ExtraProperty() {
    }

}
