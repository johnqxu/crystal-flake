package com.github.johnqxu.crystalflake.annotation;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用全局Id生成能力
 *
 * @author 徐青
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(GlobalIdConfig.class)
public @interface EnableGlobalId {
    String ENV_WORKER_KEY = "WORKER_ID";
    String ENV_DATA_CENTER_KEY = "DATA_CENTER_ID";

    long workerId() default -1;
}
