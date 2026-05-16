package com.bone.engine.extension.core.proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.engine.extension.api.spi.ExtensionPointRouter;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

/**
 * 覆盖 {@link ExtensionPointProxy} 对 {@link Object#equals}/{@link Object#hashCode}/{@link
 * Object#toString} 的约定。
 */
class ExtensionPointProxyObjectMethodsTest {

  @Test
  void objectMethodsFollowProxyIdentitySemantics() {
    @SuppressWarnings("unchecked")
    Class<Runnable> extPoint = Runnable.class;
    ApplicationContext ctx = mock(ApplicationContext.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<ExtensionPointRouter> provider = mock(ObjectProvider.class);
    when(ctx.getBeanProvider(ExtensionPointRouter.class)).thenReturn(provider);
    when(provider.getIfAvailable()).thenReturn(mock(ExtensionPointRouter.class));

    ExtensionPointProxy<Runnable> handler = new ExtensionPointProxy<>(extPoint, ctx);
    Runnable proxy =
        (Runnable)
            Proxy.newProxyInstance(extPoint.getClassLoader(), new Class<?>[] {extPoint}, handler);

    assertEquals(System.identityHashCode(proxy), proxy.hashCode());
    assertTrue(proxy.equals(proxy));
    assertFalse(proxy.equals((Object) null));
    Runnable other =
        (Runnable)
            Proxy.newProxyInstance(extPoint.getClassLoader(), new Class<?>[] {extPoint}, handler);
    assertNotEquals(proxy, other);
    assertTrue(proxy.toString().contains("Runnable"));
    assertTrue(proxy.toString().contains("ExtensionPointProxy$"));
  }
}
