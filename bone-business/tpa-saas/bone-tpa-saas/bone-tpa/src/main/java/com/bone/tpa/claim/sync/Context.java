package com.bone.tpa.claim.sync;

import com.bone.tpa.facade.vo.ColletionBindVO;
import com.bone.tpa.sdk.claim.model.Claim;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class Context {
    private String bizIdentityCode ;

    private Long claimNumber;
    // private SignRecord signRecord;

    private Claim claim;

    private Map<String,MetaExtendFieldConfig> claimExtMap = new HashMap<>() ;

    private Map<String,MetaExtendFieldConfig> invoiceExtMap= new HashMap<>();

    private Map<String,MetaExtendFieldConfig> projectExtMap= new HashMap<>() ;

    private Map<String,MetaExtendFieldConfig> mainInsurePersonExtMap= new HashMap<>();

    private Map<String,MetaExtendFieldConfig> outInsurePersonExtMap= new HashMap<>();

    private Map<String,MetaExtendFieldConfig> signRecordExtMap= new HashMap<>();

    private Map<String,MetaExtendFieldConfig> collectPersonExtMap= new HashMap<>();

    private Map<String,MetaExtendFieldConfig> collectBussinessExtMap= new HashMap<>();

    private Map<String,MetaExtendFieldConfig> projectItemExtMap= new HashMap<>();

    private Map<String,MetaExtendFieldConfig> benifitPersonExtMap= new HashMap<>();


    private Map<String, ColletionBindVO> allOptionSetMap = new HashMap<>();

    private Map<String,String> hintClaimDetail = new HashMap<>();
    private Map<Long,Map<String,String>> hintInvoice = new HashMap<>();
    private Map<Long,Map<String,String>> hintProjectInfo = new HashMap<>();
    private Map<Long,Map<String,String>> hintProjectItem = new HashMap<>();

    private Map<String,String> hintMainInsurePerson = new HashMap<>();
    private Map<String,String> hintOutInsurePerson = new HashMap<>();
    private Map<String,String> hintSignRecord = new HashMap<>();
    private Map<String,String> hintCollectPerson = new HashMap<>();
    private Map<String,String> hintCollectBussiness = new HashMap<>();
    private Map<Long,Map<String,String>> hintBenifitPerson = new HashMap<>();
}
