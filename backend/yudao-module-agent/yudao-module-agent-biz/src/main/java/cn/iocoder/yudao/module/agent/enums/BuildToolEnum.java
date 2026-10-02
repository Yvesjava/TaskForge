package cn.iocoder.yudao.module.agent.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 构建工具枚举
 *
 * @author TaskForge
 */
@Getter
@AllArgsConstructor
public enum BuildToolEnum implements ArrayValuable<String> {

    MAVEN("MAVEN", "Maven"),
    PNPM("PNPM", "pnpm"),
    GRADLE("GRADLE", "Gradle"),
    GO("GO", "Go");

    public static final String[] ARRAYS = Arrays.stream(values()).map(BuildToolEnum::getTool).toArray(String[]::new);

    /**
     * 构建工具值
     */
    private final String tool;
    /**
     * 构建工具名
     */
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
