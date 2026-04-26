package com.bone.tpa.test;

import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.masterdb.mapper.BankDictMapper;
import com.bone.tpa.sdk.masterdb.model.BankDict;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

public class BankDictMapperTest extends BaseTest{

    @Autowired
    BankDictMapper mapper;
    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimService claimService;
    @Test
    public void testByid(){
        Assert.notNull(mapper,"mapper is null");
        BankDict check =  mapper.selectById(5);
        System.out.println(check);
        Claim ex =  claimRepository.findById(1L);
        System.out.println(ex);

    }
    @Transactional(transactionManager = "masterdataTransactionManager",rollbackFor = Throwable.class)
    @Test
    public void testTransaction(){
        Claim ex =  claimRepository.findById(254700556014L);
        ex.setPlanUuid("3333");
        claimService.updateClaim(ex, false);

    }

}
