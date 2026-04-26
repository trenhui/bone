package com.bone.tpa.push.util;

import com.bone.tpa.push.service.AbstractPackageClaimService;
import com.bone.tpa.push.service.PushClaimAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author feihaiming
 *
 * @create 2025/7/28 17:15
 */
@Slf4j
public class ServiceUtil {
    public static AbstractPackageClaimService getHandler(ApplicationContext applicationContext, String insuranceName, String branchName, String insureName, AbstractPackageClaimService standardService) {
        Map<String, AbstractPackageClaimService> serviceMap = applicationContext.getBeansOfType(AbstractPackageClaimService.class).values().stream()
                .collect(Collectors.toMap(AbstractPackageClaimService::getHandlerCode, Function.identity(), (k1, k2) -> k1));
        AbstractPackageClaimService claimServiceMain = serviceMap.get(insuranceName);
        AbstractPackageClaimService claimServiceBranch = serviceMap.get(branchName);
        AbstractPackageClaimService claimServiceInsure = serviceMap.get(insureName);
        return Objects.nonNull(claimServiceInsure) ? claimServiceInsure : Objects.nonNull(claimServiceBranch) ? claimServiceBranch : Objects.nonNull(claimServiceMain) ? claimServiceMain : standardService;
    }

    public static PushClaimAction getAction(ApplicationContext applicationContext, String pushType) {
        return applicationContext.getBeansOfType(PushClaimAction.class).values().stream()
                .filter(t -> t.support(pushType))
                .findFirst()
                .orElse( null);

    }
}
