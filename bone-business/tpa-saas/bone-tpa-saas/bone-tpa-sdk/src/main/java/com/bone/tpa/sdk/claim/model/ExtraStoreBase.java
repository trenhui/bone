package com.bone.tpa.sdk.claim.model;

import com.bone.core.domain.extension.ExtensibleObject;
import lombok.Data;

@Data
public class ExtraStoreBase<ID>  extends ExtensibleObject<ID>  {

    private String extraStore;
}
