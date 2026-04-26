package com.bone.lowcode.infra.application.upload;


import com.bone.lowcode.infra.application.upload.dto.OptionSetImportDto;
import com.bone.metadata.sdk.cache.MetadataCache;

import java.util.Map;

public class UploadThreadLocal {
    public static final ThreadLocal<Map<String,String>> optionMetaLocal = new ThreadLocal<>();

    public static Map<String,String> getEntrysetLocal() {
        return optionMetaLocal.get();
    }

    public static void setEntrySetLocal(Map<String,String> meta) {
        optionMetaLocal.set(meta);
    }


    public static void removeEntrySetLocal() {
        optionMetaLocal.remove();
    }

}
