package com.bone.core.domain.extension;

import lombok.Data;

import java.io.Serializable;

/**
 * @author renhui.trh
 */
@Data
public class ExtensibleObject implements IHasExtraProperties, Serializable {
    private ExtraProperty extraProperties = new ExtraProperty();

    public ExtensibleObject() {
    }
}
