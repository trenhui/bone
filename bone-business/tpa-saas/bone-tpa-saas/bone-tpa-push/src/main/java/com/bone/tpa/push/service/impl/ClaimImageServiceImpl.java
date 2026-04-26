package com.bone.tpa.push.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.push.service.ClaimImageService;
import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.sdk.dao.ClaimImageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/10/22 15:02
 */
@Service
public class ClaimImageServiceImpl implements ClaimImageService {

    @Autowired
    private ClaimImageRepository claimImageRepository;

    @Override
    public List<ClaimImage> getClaimImageList(Long claimNo) {
        Criteria<ClaimImage> criteria = Criteria.create();
        List<ClaimImage> claimImages = claimImageRepository.findByCriteria(criteria.eq(ClaimImage::getRelatedId, claimNo).eq(ClaimImage::getPushFlag, 1));
        if (CollectionUtil.isEmpty(claimImages)) {
            return Collections.emptyList();
        }

        return claimImages;
    }
}
