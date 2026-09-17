package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.model.ParameterDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 提供经代码评审批准的平台参数注册清单。
 */
public interface ParameterDefinitionRegistry {

    /**
     * 返回全部已批准参数定义。
     *
     * @return 按参数键排序的不可变定义列表
     */
    List<ParameterDefinition> findAll();

    /**
     * 按稳定参数键查找定义。
     *
     * @param key 参数键
     * @return 已注册定义；未注册时为空
     */
    Optional<ParameterDefinition> find(String key);
}
