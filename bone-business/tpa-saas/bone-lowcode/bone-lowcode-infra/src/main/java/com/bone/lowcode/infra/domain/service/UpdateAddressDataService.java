package com.bone.lowcode.infra.domain.service;

import com.bone.lowcode.infra.domain.model.AddressData;
import com.bone.lowcode.infra.infrastructure.feign.bean.AddressInfo;
import com.bone.lowcode.infra.infrastructure.feign.bean.GroupDataParam;
import com.bone.lowcode.infra.infrastructure.feign.util.GroupDataFeignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
//@Service
public class UpdateAddressDataService {

    private final String provinceType = "province_code";
    private final String cityType = "city_code";
    private final String areaType = "area_code";


    @Autowired
    private GroupDataFeignUtil groupDataFeignUtil;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public boolean updateAddress() {
        List<AddressInfo> provinceList = getAddressDataByType(provinceType);
        List<String> provinceCode = provinceList.stream().map(AddressInfo::getCode).toList();

        List<AddressInfo> cityList = getAddressDataByType(cityType);
        Map<String, List<AddressInfo>> cityMap = cityList.stream().collect(Collectors.groupingBy(AddressInfo::getParentCode));

        List<AddressInfo> areaList = getAddressDataByType(areaType);
        Map<String, List<AddressInfo>> areaMap = areaList.stream().collect(Collectors.groupingBy(AddressInfo::getParentCode));

        List<AddressData> provinceDataList = provinceList.stream().map(province -> {
            AddressData addressData = new AddressData();
            BeanUtils.copyProperties(province, addressData);
            List<AddressInfo> subCityList = cityMap.get(province.getCode());
            if (!CollectionUtils.isEmpty(subCityList)) {
                List<String> list = subCityList.stream().map(AddressInfo::getCode).toList();
                addressData.setSubElementCodeList(list);
            }
            return addressData;
        }).toList();

        List<AddressData> cityDataList = cityList.stream().map(city -> {
            AddressData addressData = new AddressData();
            BeanUtils.copyProperties(city, addressData);
            List<AddressInfo> subAreaList = areaMap.get(city.getCode());
            if (!CollectionUtils.isEmpty(subAreaList)) {
                List<String> list = subAreaList.stream().map(AddressInfo::getCode).toList();
                addressData.setSubElementCodeList(list);
            }
            return addressData;
        }).toList();

        List<AddressData> areaDataList = areaList.stream().map(area -> {
            AddressData addressData = new AddressData();
            BeanUtils.copyProperties(area, addressData);
            return addressData;
        }).toList();

        String rootKey = "masterData:root";//存放所有根节点的key
        String prefix = "address:";
        String addressRoot = "addressRoot"; //省市区根节点
        deleteKeysByPrefix(prefix);
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.opsForSet().add(rootKey, addressRoot);
                operations.opsForHash().putAll(
                        prefix + addressRoot,
                        Map.of(
                                "type", "addressRoot",
                                "name", "省市区",
                                "code", addressRoot,
                                "subItem", CollectionUtils.isEmpty(provinceCode) ? List.of() : provinceCode
                        ));

                for (AddressData provinceData : provinceDataList) {
                    operations.opsForHash().putAll(
                            prefix + provinceData.getCode(),
                            Map.of(
                                    "type", "province",
                                    "name", provinceData.getName(),
                                    "code", provinceData.getCode(),
                                    "subItem", CollectionUtils.isEmpty(provinceData.getSubElementCodeList()) ? List.of() : provinceData.getSubElementCodeList()
                            ));
                }

                for (AddressData cityData : cityDataList) {
                    operations.opsForHash().putAll(
                            prefix + cityData.getCode(),
                            Map.of(
                                    "type", "city",
                                    "name", cityData.getName(),
                                    "code", cityData.getCode(),
                                    "subItem", CollectionUtils.isEmpty(cityData.getSubElementCodeList()) ? List.of() : cityData.getSubElementCodeList()
                            ));
                }

                for (AddressData areaData : areaDataList) {
                    operations.opsForHash().putAll(
                            prefix + areaData.getCode(),
                            Map.of(
                                    "type", "area",
                                    "name", areaData.getName(),
                                    "code", areaData.getCode(),
                                    "subItem", List.of()
                            ));
                }
                return null;
            }
        });

        return true;
    }


    public void deleteKeysByPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return;
        }
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        Long delete = redisTemplate.delete(keys);
        log.info("prefix:{}, deleteCount:{}", prefix, delete);
    }


    private List<AddressInfo> getAddressDataByType(String addressType) {
        GroupDataParam param = new GroupDataParam();
        param.setType(addressType);
        return groupDataFeignUtil.getAddressList(param).getData();
    }
}
