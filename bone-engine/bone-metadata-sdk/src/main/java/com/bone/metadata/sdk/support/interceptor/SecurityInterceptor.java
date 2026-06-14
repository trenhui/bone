package com.bone.metadata.sdk.support.interceptor;

import com.bone.metadata.sdk.support.security.service.MetaPermissionService;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class SecurityInterceptor {
  private final MetaPermissionService permissionService;

  public SecurityInterceptor(MetaPermissionService permissionService) {
    this.permissionService = permissionService;
  }

  public <T> T wrapService(Class<T> serviceInterface, T implementation) {
    return serviceInterface.cast(
        Proxy.newProxyInstance(
            serviceInterface.getClassLoader(),
            new Class<?>[] {serviceInterface},
            new SecurityInvocationHandler(serviceInterface, implementation)));
  }

  private class SecurityInvocationHandler implements InvocationHandler {
    private final Class<?> serviceInterface;
    private final Object target;

    public SecurityInvocationHandler(Class<?> serviceInterface, Object target) {
      this.serviceInterface = serviceInterface;
      this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String permission = serviceInterface.getSimpleName() + "." + method.getName();
      if (!permissionService.hasPermission(permission)) {
        throw new SecurityException("Access denied: " + permission);
      }
      return method.invoke(target, args);
    }
  }
}
