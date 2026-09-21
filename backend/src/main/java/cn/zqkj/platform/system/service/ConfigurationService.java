package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.DeleteParameterCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.domain.dto.ExternalEndpointCommand;
import cn.zqkj.platform.system.domain.dto.ExternalSystemCommand;
import cn.zqkj.platform.system.domain.model.AccessActor;
import cn.zqkj.platform.system.domain.vo.DictionaryItemVO;
import cn.zqkj.platform.system.domain.vo.DictionaryTypeVO;
import cn.zqkj.platform.system.domain.vo.ParameterDefinitionVO;
import cn.zqkj.platform.system.domain.vo.ParameterValueVO;
import cn.zqkj.platform.system.domain.vo.ExternalEndpointVO;
import cn.zqkj.platform.system.domain.vo.ExternalEndpointAuthenticationVO;
import cn.zqkj.platform.system.domain.vo.ExternalSystemVO;

import java.util.List;

/**
 * 管理代码注册参数和平台系统字典的业务边界。
 */
public interface ConfigurationService {

    /**
     * 查询由代码注册且可供管理端配置的参数定义。
     *
     * @return 代码注册参数定义
     */
    List<ParameterDefinitionVO> findParameterDefinitions();

    /**
     * 查询操作人机构范围内及全局的参数值。
     *
     * @param actor 操作人
     * @return 操作人机构范围内及全局的参数值
     */
    List<ParameterValueVO> findParameterValues(AccessActor actor);

    /**
     * 按参数键和适用范围新增或更新参数值。
     *
     * @param key 参数键
     * @param command 写入命令
     * @param actor 操作人
     * @return 写入后的安全参数值
     */
    ParameterValueVO upsertParameter(String key, UpsertParameterCommand command, AccessActor actor);

    /**
     * 按适用范围和并发版本删除参数值。
     *
     * @param key 参数键
     * @param command 删除命令
     * @param actor 操作人
     */
    void deleteParameter(String key, DeleteParameterCommand command, AccessActor actor);

    /**
     * 查询全部平台系统字典类型。
     *
     * @return 全部平台系统字典类型
     */
    List<DictionaryTypeVO> findDictionaryTypes();

    /**
     * 创建系统字典类型。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新字典类型
     */
    DictionaryTypeVO createDictionaryType(CreateDictionaryTypeCommand command, AccessActor actor);

    /**
     * 使用行版本更新系统字典类型。
     *
     * @param typeId 类型主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后类型
     */
    DictionaryTypeVO updateDictionaryType(long typeId, UpdateDictionaryTypeCommand command, AccessActor actor);

    /**
     * 删除未包含字典项的系统字典类型。
     *
     * @param typeId 类型主键
     * @param expectedVersion 当前并发版本
     * @param actor 操作人
     */
    void deleteDictionaryType(long typeId, byte[] expectedVersion, AccessActor actor);

    /**
     * 查询稳定排序字典项。
     *
     * @param typeId 类型主键
     * @param includeDisabled 是否包含停用项
     * @return 稳定排序字典项
     */
    List<DictionaryItemVO> findDictionaryItems(long typeId, boolean includeDisabled);

    /**
     * 在指定字典类型下创建字典项。
     *
     * @param typeId 类型主键
     * @param command 创建命令
     * @param actor 操作人
     * @return 新字典项
     */
    DictionaryItemVO createDictionaryItem(long typeId, CreateDictionaryItemCommand command, AccessActor actor);

    /**
     * 使用行版本更新系统字典项。
     *
     * @param itemId 字典项主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后字典项
     */
    DictionaryItemVO updateDictionaryItem(long itemId, UpdateDictionaryItemCommand command, AccessActor actor);

    /**
     * 删除未被业务外键引用的系统字典项。
     *
     * @param itemId 字典项主键
     * @param expectedVersion 当前并发版本
     * @param actor 操作人
     */
    void deleteDictionaryItem(long itemId, byte[] expectedVersion, AccessActor actor);

    /**
     * 查询已确认外部系统。
     *
     * @return 已确认外部系统
     */
    List<ExternalSystemVO> findExternalSystems();

    /**
     * 创建外部系统登记信息。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新外部系统
     */
    ExternalSystemVO createExternalSystem(ExternalSystemCommand command, AccessActor actor);

    /**
     * 使用行版本更新外部系统登记信息。
     *
     * @param systemId 系统主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后系统
     */
    ExternalSystemVO updateExternalSystem(long systemId, ExternalSystemCommand command, AccessActor actor);

    /**
     * 查询授权范围内服务地址。
     *
     * @param systemId 系统主键
     * @param actor 操作人
     * @return 授权范围内服务地址
     */
    List<ExternalEndpointVO> findExternalEndpoints(long systemId, AccessActor actor);

    /**
     * 查询授权范围内按需回显的接入信息。
     *
     * @param endpointId 服务地址主键
     * @param actor 操作人
     * @return 授权范围内按需回显的接入信息
     */
    ExternalEndpointAuthenticationVO findExternalEndpointAuthentication(long endpointId, AccessActor actor);

    /**
     * 创建尚未投入业务运行的外部系统端点。
     *
     * @param systemId 系统主键
     * @param command 创建命令
     * @param actor 操作人
     * @return 新服务地址
     */
    ExternalEndpointVO createExternalEndpoint(long systemId, ExternalEndpointCommand command, AccessActor actor);

    /**
     * 使用行版本更新端点并使旧验证结果失效。
     *
     * @param endpointId 服务地址主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改后服务地址
     */
    ExternalEndpointVO updateExternalEndpoint(long endpointId, ExternalEndpointCommand command, AccessActor actor);

    /**
     * 调用100-008确认当前保存的基层HIS配置所对应的唯一来源机构。
     *
     * @param endpointId 服务地址主键
     * @param actor 操作人
     * @return 已写回自动校验结果的服务地址
     */
    ExternalEndpointVO verifyExternalEndpoint(long endpointId, AccessActor actor);
}
