package cn.zqkj.platform.system.service;

import cn.zqkj.platform.system.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryTypeCommand;
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
import cn.zqkj.platform.system.domain.vo.ExternalSystemVO;

import java.util.List;

/**
 * 管理代码注册参数和平台系统字典的业务边界。
 */
public interface ConfigurationService {

    /** @return 代码注册参数定义 */
    List<ParameterDefinitionVO> findParameterDefinitions();

    /** @param actor 操作人 @return 操作人机构范围内及全局的参数值 */
    List<ParameterValueVO> findParameterValues(AccessActor actor);

    /** @param key 参数键 @param command 写入命令 @param actor 操作人 @return 写入后的安全参数值 */
    ParameterValueVO upsertParameter(String key, UpsertParameterCommand command, AccessActor actor);

    /** @return 全部平台系统字典类型 */
    List<DictionaryTypeVO> findDictionaryTypes();

    /** @param command 创建命令 @param actor 操作人 @return 新字典类型 */
    DictionaryTypeVO createDictionaryType(CreateDictionaryTypeCommand command, AccessActor actor);

    /** @param typeId 类型主键 @param command 修改命令 @param actor 操作人 @return 修改后类型 */
    DictionaryTypeVO updateDictionaryType(long typeId, UpdateDictionaryTypeCommand command, AccessActor actor);

    /** @param typeId 类型主键 @param includeDisabled 是否包含停用项 @return 稳定排序字典项 */
    List<DictionaryItemVO> findDictionaryItems(long typeId, boolean includeDisabled);

    /** @param typeId 类型主键 @param command 创建命令 @param actor 操作人 @return 新字典项 */
    DictionaryItemVO createDictionaryItem(long typeId, CreateDictionaryItemCommand command, AccessActor actor);

    /** @param itemId 字典项主键 @param command 修改命令 @param actor 操作人 @return 修改后字典项 */
    DictionaryItemVO updateDictionaryItem(long itemId, UpdateDictionaryItemCommand command, AccessActor actor);

    /** @return 已确认外部系统 */
    List<ExternalSystemVO> findExternalSystems();

    /** @param command 创建命令 @param actor 操作人 @return 新外部系统 */
    ExternalSystemVO createExternalSystem(ExternalSystemCommand command, AccessActor actor);

    /** @param systemId 系统主键 @param command 修改命令 @param actor 操作人 @return 修改后系统 */
    ExternalSystemVO updateExternalSystem(long systemId, ExternalSystemCommand command, AccessActor actor);

    /** @param systemId 系统主键 @param actor 操作人 @return 授权范围内端点 */
    List<ExternalEndpointVO> findExternalEndpoints(long systemId, AccessActor actor);

    /** @param systemId 系统主键 @param command 创建命令 @param actor 操作人 @return 新端点 */
    ExternalEndpointVO createExternalEndpoint(long systemId, ExternalEndpointCommand command, AccessActor actor);

    /** @param endpointId 端点主键 @param command 修改命令 @param actor 操作人 @return 修改后端点 */
    ExternalEndpointVO updateExternalEndpoint(long endpointId, ExternalEndpointCommand command, AccessActor actor);
}
