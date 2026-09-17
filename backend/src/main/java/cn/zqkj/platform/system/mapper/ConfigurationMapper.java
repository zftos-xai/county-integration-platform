package cn.zqkj.platform.system.mapper;

import cn.zqkj.platform.system.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.domain.dto.ExternalEndpointCommand;
import cn.zqkj.platform.system.domain.dto.ExternalSystemCommand;
import cn.zqkj.platform.system.domain.model.DictionaryItem;
import cn.zqkj.platform.system.domain.model.DictionaryType;
import cn.zqkj.platform.system.domain.model.ParameterValue;
import cn.zqkj.platform.system.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.domain.model.ExternalSystem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 读写代码注册参数值和平台系统字典的MyBatis Mapper。
 */
@Mapper
public interface ConfigurationMapper {

    /** @return 按参数键和作用域稳定排序的全部参数值 */
    List<ParameterValue> findParameterValues();

    /** @param key 参数键 @param command 作用域命令 @return 匹配作用域值；不存在时为空 */
    Optional<ParameterValue> findParameterValue(
            @Param("key") String key,
            @Param("command") UpsertParameterCommand command
    );

    /** @param key 参数键 @param valueType 注册类型 @param command 写入命令 @param actor 操作人 @return 新主键 */
    long createParameterValue(
            @Param("key") String key,
            @Param("valueType") String valueType,
            @Param("command") UpsertParameterCommand command,
            @Param("actor") String actor
    );

    /** @param id 参数值主键 @param valueType 注册类型 @param command 修改命令 @param actor 操作人 @return 修改行数 */
    int updateParameterValue(
            @Param("id") long id,
            @Param("valueType") String valueType,
            @Param("command") UpsertParameterCommand command,
            @Param("actor") String actor
    );

    /** @return 按类型代码稳定排序的字典类型 */
    List<DictionaryType> findDictionaryTypes();

    /** @param typeId 字典类型主键 @return 字典类型；不存在时为空 */
    Optional<DictionaryType> findDictionaryType(long typeId);

    /** @param typeCode 类型代码 @return 已存在时为true */
    boolean dictionaryTypeCodeExists(String typeCode);

    /** @param command 创建命令 @param actor 操作人 @return 新类型主键 */
    long createDictionaryType(
            @Param("command") CreateDictionaryTypeCommand command,
            @Param("actor") String actor
    );

    /** @param typeId 类型主键 @param command 修改命令 @param actor 操作人 @return 修改行数 */
    int updateDictionaryType(
            @Param("typeId") long typeId,
            @Param("command") UpdateDictionaryTypeCommand command,
            @Param("actor") String actor
    );

    /** @param typeId 类型主键 @param includeDisabled 是否包含停用项 @return 按顺序和代码排序的字典项 */
    List<DictionaryItem> findDictionaryItems(
            @Param("typeId") long typeId,
            @Param("includeDisabled") boolean includeDisabled
    );

    /** @param itemId 字典项主键 @return 字典项；不存在时为空 */
    Optional<DictionaryItem> findDictionaryItem(long itemId);

    /** @param typeId 类型主键 @param itemCode 项代码 @return 已存在时为true */
    boolean dictionaryItemCodeExists(@Param("typeId") long typeId, @Param("itemCode") String itemCode);

    /** @param typeId 类型主键 @param command 创建命令 @param actor 操作人 @return 新字典项主键 */
    long createDictionaryItem(
            @Param("typeId") long typeId,
            @Param("command") CreateDictionaryItemCommand command,
            @Param("actor") String actor
    );

    /** @param itemId 字典项主键 @param command 修改命令 @param actor 操作人 @return 修改行数 */
    int updateDictionaryItem(
            @Param("itemId") long itemId,
            @Param("command") UpdateDictionaryItemCommand command,
            @Param("actor") String actor
    );

    /** @return 稳定排序外部系统 */
    List<ExternalSystem> findExternalSystems();

    /** @param systemId 主键 @return 外部系统 */
    Optional<ExternalSystem> findExternalSystem(long systemId);

    /** @param systemCode 稳定代码 @return 是否存在 */
    boolean externalSystemCodeExists(String systemCode);

    /** @param command 创建命令 @param actor 操作人 @return 新主键 */
    long createExternalSystem(@Param("command") ExternalSystemCommand command, @Param("actor") String actor);

    /** @param systemId 主键 @param command 修改命令 @param actor 操作人 @return 修改行数 */
    int updateExternalSystem(@Param("systemId") long systemId,
                             @Param("command") ExternalSystemCommand command,
                             @Param("actor") String actor);

    /** @param systemId 系统主键 @return 稳定排序端点 */
    List<ExternalEndpoint> findExternalEndpoints(long systemId);

    /** @param endpointId 主键 @return 端点 */
    Optional<ExternalEndpoint> findExternalEndpoint(long endpointId);

    /** @param systemId 系统主键 @param command 作用域命令 @return 是否存在 */
    boolean externalEndpointScopeExists(@Param("systemId") long systemId,
                                        @Param("command") ExternalEndpointCommand command);

    /** @param systemId 系统主键 @param command 创建命令 @param actor 操作人 @return 新主键 */
    long createExternalEndpoint(@Param("systemId") long systemId,
                                @Param("command") ExternalEndpointCommand command,
                                @Param("actor") String actor);

    /** @param endpointId 主键 @param command 修改命令 @param actor 操作人 @return 修改行数 */
    int updateExternalEndpoint(@Param("endpointId") long endpointId,
                               @Param("command") ExternalEndpointCommand command,
                               @Param("actor") String actor);
}
