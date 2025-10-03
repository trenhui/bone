package com.bone.integration.infrastructure.converter;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.bone.integration.application.dto.MockDTO;
import com.bone.integration.domain.model.MockDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MockConverter {
    public static List<MockDTO> convert(List<MockDO> poList) {
        if (poList == null || poList.isEmpty()) {
            return new ArrayList<>();
        }
        List<MockDTO> dtoList = new ArrayList<>();
        for (MockDO po : poList) {
            dtoList.add(convert(po));
        }
        return dtoList;
    }

    public static MockDTO convert(MockDO po) {
        if (po == null) {
            return null;
        }

        MockDTO mockDTO = new MockDTO();
        BeanUtils.copyProperties(po, mockDTO);

        String conditions = po.getConditions();
        List<MockDTO.MockCondition> conditionList = JSONUtil.toList(conditions, MockDTO.MockCondition.class);
        mockDTO.setConditions(conditionList);
        return mockDTO;
    }

    public static MockDO convert(MockDTO mockDTO) {
        MockDO entity = new MockDO();
        BeanUtils.copyProperties(mockDTO, entity);
        List<MockDTO.MockCondition> conditionList = mockDTO.getConditions();
        for (MockDTO.MockCondition condition : conditionList) {
            MockDTO.MockResponse mockResponse = condition.getResponse();
            List<MockDTO.ResponseHeader> responseHeaderList = mockResponse.getResponseHeader();
            Iterator<MockDTO.ResponseHeader> iterator = responseHeaderList.iterator();
            while (iterator.hasNext()) {
                MockDTO.ResponseHeader responseHeader = iterator.next();
                if (StringUtils.isEmpty(responseHeader.getKey())) {
                    // iterator.remove();
                }
            }
        }
        entity.setConditions(new JSONArray(conditionList).toString());

        return entity;
    }
}
