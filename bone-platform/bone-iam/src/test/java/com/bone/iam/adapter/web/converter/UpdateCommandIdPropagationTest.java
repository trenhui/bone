package com.bone.iam.adapter.web.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bone.iam.adapter.web.dto.request.UpdateAppReq;
import com.bone.iam.adapter.web.dto.request.UpdateModuleReq;
import com.bone.iam.application.command.UpdateApplicationCommand;
import com.bone.iam.application.command.UpdateModuleCommand;
import org.junit.jupiter.api.Test;

/**
 * 更新命令的主键回填守卫（联调实测缺陷回归）。
 *
 * <p><b>缺陷：</b>{@code PUT /api/v1/apps/{id}} 与 {@code PUT /api/v1/apps/{appId}/modules/{id}} 的
 * converter 收了路径变量 {@code id} 却从未 {@code cmd.setId(id)}；下游以 {@code null} 主键查库 ⇒ 500， 且
 * UI「编辑应用/编辑模块」保存后改动<b>静默不生效</b>。
 *
 * <p><b>为什么断言在 converter 层：</b>缺陷在「请求 → 命令」边界，service 层 mock 单测看不到它。
 *
 * <p><b>负向探针：</b>删掉 {@code AppWebConverter#toUpdateCommand} 的 {@code cmd.setId(id);}， {@link
 * #appUpdateCommandCarriesPathId()} 必须变红；模块侧同理。
 */
class UpdateCommandIdPropagationTest {

  private static final Long APP_ID = 764874469893210112L;
  private static final Long MODULE_ID = 764874230620749824L;

  @Test
  void appUpdateCommandCarriesPathId() {
    UpdateAppReq req = new UpdateAppReq();
    req.setName("改名后的应用");
    req.setDescription("联调实测");
    req.setIcon("AppstoreOutlined");
    req.setStatus(1);

    UpdateApplicationCommand cmd = new AppWebConverter().toUpdateCommand(req, APP_ID);

    assertEquals(APP_ID, cmd.getId(), "应用更新命令必须携带路径变量 id，否则 service 以 null 主键查库 ⇒ 500");
    assertEquals("改名后的应用", cmd.getName());
  }

  @Test
  void moduleUpdateCommandCarriesPathId() {
    UpdateModuleReq req = new UpdateModuleReq();
    req.setName("改名后的模块");
    req.setDescription("联调实测");
    req.setStatus(1);

    UpdateModuleCommand cmd = new ModuleWebConverter().toUpdateCommand(req, MODULE_ID);

    assertEquals(MODULE_ID, cmd.getId(), "模块更新命令必须携带路径变量 id，否则 service 以 null 主键查库 ⇒ 500");
    assertEquals("改名后的模块", cmd.getName());
  }

  /** 两个 converter 必须各自回填自己的 id，不能互相串味（防复制粘贴改一半）。 */
  @Test
  void idIsBoundPerCallNotShared() {
    UpdateAppReq appReq = new UpdateAppReq();
    appReq.setName("A");
    UpdateApplicationCommand appCmd = new AppWebConverter().toUpdateCommand(appReq, 111L);

    UpdateModuleReq modReq = new UpdateModuleReq();
    modReq.setName("M");
    UpdateModuleCommand modCmd = new ModuleWebConverter().toUpdateCommand(modReq, 222L);

    assertNotNull(appCmd.getId());
    assertNotNull(modCmd.getId());
    assertNotEquals(appCmd.getId(), modCmd.getId());
  }
}
