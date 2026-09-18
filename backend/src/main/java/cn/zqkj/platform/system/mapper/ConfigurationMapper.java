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
import cn.zqkj.platform.system.domain.model.ExternalEndpointCredential;
import cn.zqkj.platform.system.domain.model.ExternalSystem;
import cn.zqkj.platform.system.domain.model.EncryptedExternalEndpointCredential;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 读写代码注册参数值和平台系统字典的MyBatis Mapper。
 */
@Mapper
public interface ConfigurationMapper {

    /** @return 按参数键和适用范围稳定排序的全部参数值 */
    List<ParameterValue> findParameterValues();

    /** @param key 参数键 @param command 适用范围命令 @return 匹配按适用范围保存的值；不存在时为空 */
    Optional<ParameterValue> findParameterValue(
            @Param("key") String key,
            @Param("command") UpsertParameterCommand command
    );

    /** @param id 参数值主键 @return 匹配主键的参数值；不存在时为空 */
    Optional<ParameterValue> findParameterValueById(long id);

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

    /** @param id 参数值主键 @param expectedVersion 当前并发版本 @return 删除行数 */
    int deleteParameterValue(@Param("id") long id, @Param("expectedVersion") byte[] expectedVersion);

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

    /** @param typeId 类型主键 @return 类型下的字典项数量 */
    long countDictionaryItems(long typeId);

    /** @param typeId 类型主键 @param expectedVersion 当前并发版本 @return 删除行数 */
    int deleteDictionaryType(@Param("typeId") long typeId, @Param("expectedVersion") byte[] expectedVersion);

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

    /**
     * @param itemId 字典项主键
     * @return 数据库中通过外键实际引用该字典项的业务记录数量
     */
    long countDictionaryItemReferences(long itemId);

    /** @param itemId 字典项主键 @param expectedVersion 当前并发版本 @return 删除行数 */
    int deleteDictionaryItem(@Param("itemId") long itemId, @Param("expectedVersion") byte[] expectedVersion);

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

    /** @param systemId 系统主键 @return 稳定排序服务地址 */
    List<ExternalEndpoint> findExternalEndpoints(long systemId);

    /** @param endpointId 主键 @return 服务地址 */
    Optional<ExternalEndpoint> findExternalEndpoint(long endpointId);

    /**
     * @param systemCode 外部系统稳定代码
     * @param environment 部署环境
     * @param organizationId 机构主键
     * @return 当前机构在指定环境下启用的服务地址；不存在时为空
     */
    Optional<ExternalEndpoint> findEnabledExternalEndpoint(
            @Param("systemCode") String systemCode,
            @Param("environment") cn.zqkj.platform.system.domain.model.ParameterEnvironment environment,
            @Param("organizationId") long organizationId
    );

    /** @param systemId 系统主键 @param excludedEndpointId 修改时排除的当前配置主键 @param command 适用范围命令 @return 是否存在 */
    boolean externalEndpointScopeExists(@Param("systemId") long systemId,
                                        @Param("excludedEndpointId") Long excludedEndpointId,
                                        @Param("command") ExternalEndpointCommand command);

    /** @param systemId 系统主键 @param command 创建命令 @param actor 操作人 @return 新主键 */
    long createExternalEndpoint(@Param("systemId") long systemId,
                                @Param("command") ExternalEndpointCommand command,
                                @Param("actor") String actor);

    /** @param endpointId 主键 @param command 修改命令 @param actor 操作人 @return 修改行数 */
    int updateExternalEndpoint(@Param("endpointId") long endpointId,
                               @Param("command") ExternalEndpointCommand command,
                               @Param("actor") String actor);

    /** @param endpointId 服务地址主键 @return 已保存认证密文；不存在时为空 */
    Optional<ExternalEndpointCredential> findExternalEndpointCredential(long endpointId);

    /** @param endpointId 服务地址主键 @param credential 认证密文 @param actor 操作人 */
    void createExternalEndpointCredential(
            @Param("endpointId") long endpointId,
            @Param("credential") EncryptedExternalEndpointCredential credential,
            @Param("actor") String actor
    );

    /** @param endpointId 服务地址主键 @param credential 新认证密文 @param actor 操作人 @return 修改行数 */
    int updateExternalEndpointCredential(
            @Param("endpointId") long endpointId,
            @Param("credential") EncryptedExternalEndpointCredential credential,
            @Param("actor") String actor
    );
}
