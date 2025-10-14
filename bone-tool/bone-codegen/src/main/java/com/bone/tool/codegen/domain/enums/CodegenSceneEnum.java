package com.bone.tool.codegen.domain.enums;

import lombok.Getter;

import static cn.hutool.core.util.ArrayUtil.firstMatch;

/**
 * 代码生成的场景枚举
 *
 * @author bone-team
 */
@Getter
public enum CodegenSceneEnum {

    ADMIN(1, "管理后台", "admin", ""),
    APP(2, "用户 APP", "app", "App");

    /**
     * 场景
     */
    private final Integer scene;
    /**
     * 场景名
     */
    private final String name;
    /**
     * 基础包名
     */
    private final String basePackage;
    /**
     * Controller 和 VO 类的前缀
     */
    private final String prefixClass;

    CodegenSceneEnum(int scene, String name, String basePackage, String prefixClass) {
        this.scene = scene;
        this.name = name;
        this.basePackage = basePackage;
        this.prefixClass = prefixClass;
    }

    public static CodegenSceneEnum valueOf(Integer scene) {
        return firstMatch(sceneEnum -> sceneEnum.scene.equals(scene), values());
    }

}
