package com.bone.tpa.core.hook;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.sdk.dao.biz.ClaimHookConfigBiz;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import com.bone.tpa.sdk.identityRule.invoice.hospitalName.InvoiceHosptialCalHook;
import com.bone.tpa.sdk.util.SpringContextUtils;
import com.bone.tpa.sdk.vo.HookPageconfigVO;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BizHookFactory  {
    @Autowired
    private ClaimHookConfigBiz hookConfigBiz;




    public InvoiceHosptialCalHook getInvoiceHosptialCalHook(String bizIdentityCode){
        List<BizidentityHook> hookList =  getRuntimeHooks(InvoiceHosptialCalHook.domain, InvoiceHosptialCalHook.beanType,
                bizIdentityCode);
        BizidentityHook first = PkListUtil.first(hookList);
        if(first == null){
            return null;
        }
        if(first instanceof InvoiceHosptialCalHook){
            return (InvoiceHosptialCalHook)first;
        }
        return  null;
    }

    /**
     * 在执行hook时候，结合配置获取开放的hook
     * @param domain
     * @param beanType
     * @param bizIdentityCode
     * @return
     */
    public List<BizidentityHook> getRuntimeHooks(String domain,String beanType,String bizIdentityCode){
        List<BizidentityHook>  dictList =    getHooksByType(domain,beanType);
        List<BizidentityHook> rs = new ArrayList<>();
        Map<String, HookPageconfigVO>  configedMap =  hookConfigBiz.getHookConfigByMap(bizIdentityCode);
        for(BizidentityHook hook:dictList){
            String beanName = hook.getBeanName();
            HookPageconfigVO hookPageconfigVO =  configedMap.get(beanName);
            if( hookPageconfigVO == null){
                continue;
            }
            if( hookPageconfigVO.getStatus()== null ||hookPageconfigVO.getStatus() == 0){
                continue;
            }
            rs.add(hook);
        }
        return rs;
    }


    public List<BizidentityHook> getHooksByType(String domain,String beanType){
        Assert.notNull(domain,"domain is null");
        Assert.notNull(beanType,"beanType is null");
        return getHookList().values().stream().filter(t->{
            return domain.equals(t.getDomain()) && beanType.equals(t.getBeanType());
        }).collect(Collectors.toList());
    }
    /**
     * Invoked by the containing {@code BeanFactory} after it has set all bean properties
     * and satisfied {@link BeanFactoryAware}, {@code ApplicationContextAware} etc.
     * <p>This method allows the bean instance to perform validation of its overall
     * configuration and final initialization when all bean properties have been set.
     *
     * @throws Exception in the event of misconfiguration (such as failure to set an
     *                   essential property) or if initialization fails for any other reason
     */

    public  Map<String, BizidentityHook> getHookList()   {
        // BizidentityHook<ClaimUpdateHookParam,Boolean>
        Map<String,BizidentityHook> beanMap = SpringContextUtils.getBeansOfType(BizidentityHook.class);
        Map<String, BizidentityHook> rs = new HashMap<>();
        beanMap.entrySet().stream().forEach(t->{
            String key = t.getKey();
            BizidentityHook bizidentityHook = t.getValue();
            if(rs.containsKey(key)){
                throw new RuntimeException("bizHookMap contains key:"+key);
            }
            rs.put(key,bizidentityHook);
        });
        return rs;
    }



}
