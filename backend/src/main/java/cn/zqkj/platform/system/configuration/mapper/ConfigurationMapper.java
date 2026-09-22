package cn.zqkj.platform.system.configuration.mapper;

import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.CreateExternalSystemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryItemCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateDictionaryTypeCommand;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalEndpointRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpdateExternalSystemRequest;
import cn.zqkj.platform.system.configuration.domain.dto.UpsertParameterCommand;
import cn.zqkj.platform.system.configuration.domain.model.DictionaryItem;
import cn.zqkj.platform.system.configuration.domain.model.DictionaryType;
import cn.zqkj.platform.system.configuration.domain.model.EncryptedExternalEndpointCredential;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpoint;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointCredential;
import cn.zqkj.platform.system.configuration.domain.model.ExternalEndpointScope;
import cn.zqkj.platform.system.configuration.domain.model.ExternalSystem;
import cn.zqkj.platform.system.configuration.domain.model.ParameterEnvironment;
import cn.zqkj.platform.system.configuration.domain.model.ParameterValue;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 读写代码注册参数值和平台系统字典的MyBatis Mapper。
 */
@Mapper
public interface ConfigurationMapper {

    /**
     * 按参数键和环境查询全局及获准机构的参数值。
     *
     * @param organizationCodes 调用人获准访问的机构代码；空列表仅返回全局参数
     * @return 按参数键和适用范围稳定排序的可见参数值
     */
    List<ParameterValue> findParameterValues(@Param("organizationCodes") List<String> organizationCodes);

    /**
     * 查询匹配按适用范围保存的值；不存在时为空。
     *
     * @param key 参数键
     * @param environment 端点使用环境
     * @param organizationId 可选平台机构主键
     * @return 匹配按适用范围保存的值；不存在时为空
     */
    Optional<ParameterValue> findParameterValue(
            @Param("key") String key,
            @Param("environment") ParameterEnvironment environment,
            @Param("organizationId") Long organizationId
    );

    /**
     * 查询匹配主键的参数值；不存在时为空。
     *
     * @param id 参数值主键
     * @return 匹配主键的参数值；不存在时为空
     */
    Optional<ParameterValue> findParameterValueById(long id);

    /**
     * 写入一个已通过注册规则校验的参数值。
     *
     * @param key 参数键
     * @param valueType 注册类型
     * @param command 写入命令
     * @param actor 操作人
     * @return 新主键
     */
    long createParameterValue(
            @Param("key") String key,
            @Param("valueType") String valueType,
            @Param("command") UpsertParameterCommand command,
            @Param("actor") String actor
    );

    /**
     * 使用行版本更新指定适用范围的参数值。
     *
     * @param id 参数值主键
     * @param valueType 注册类型
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改行数
     */
    int updateParameterValue(
            @Param("id") long id,
            @Param("valueType") String valueType,
            @Param("command") UpsertParameterCommand command,
            @Param("actor") String actor
    );

    /**
     * 使用行版本删除指定参数值。
     *
     * @param id 参数值主键
     * @param expectedVersion 当前并发版本
     * @return 删除行数
     */
    int deleteParameterValue(@Param("id") long id, @Param("expectedVersion") byte[] expectedVersion);

    /**
     * 查询按类型代码稳定排序的字典类型。
     *
     * @return 按类型代码稳定排序的字典类型
     */
    List<DictionaryType> findDictionaryTypes();

    /**
     * 查询字典类型；不存在时为空。
     *
     * @param typeId 字典类型主键
     * @return 字典类型；不存在时为空
     */
    Optional<DictionaryType> findDictionaryType(long typeId);

    /**
     * 判断系统字典类型代码是否已被占用。
     *
     * @param typeCode 类型代码
     * @return 已存在时为true
     */
    boolean dictionaryTypeCodeExists(String typeCode);

    /**
     * 创建系统字典类型。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新类型主键
     */
    long createDictionaryType(
            @Param("command") CreateDictionaryTypeCommand command,
            @Param("actor") String actor
    );

    /**
     * 使用行版本更新系统字典类型。
     *
     * @param typeId 类型主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改行数
     */
    int updateDictionaryType(
            @Param("typeId") long typeId,
            @Param("command") UpdateDictionaryTypeCommand command,
            @Param("actor") String actor
    );

    /**
     * 统计类型下的字典项数量。
     *
     * @param typeId 类型主键
     * @return 类型下的字典项数量
     */
    long countDictionaryItems(long typeId);

    /**
     * 删除未包含字典项的系统字典类型。
     *
     * @param typeId 类型主键
     * @param expectedVersion 当前并发版本
     * @return 删除行数
     */
    int deleteDictionaryType(@Param("typeId") long typeId, @Param("expectedVersion") byte[] expectedVersion);

    /**
     * 查询按顺序和代码排序的字典项。
     *
     * @param typeId 类型主键
     * @param includeDisabled 是否包含停用项
     * @return 按顺序和代码排序的字典项
     */
    List<DictionaryItem> findDictionaryItems(
            @Param("typeId") long typeId,
            @Param("includeDisabled") boolean includeDisabled
    );

    /**
     * 查询字典项；不存在时为空。
     *
     * @param itemId 字典项主键
     * @return 字典项；不存在时为空
     */
    Optional<DictionaryItem> findDictionaryItem(long itemId);

    /**
     * 判断同一字典类型内的字典项代码是否已被占用。
     *
     * @param typeId 类型主键
     * @param itemCode 项代码
     * @return 已存在时为true
     */
    boolean dictionaryItemCodeExists(@Param("typeId") long typeId, @Param("itemCode") String itemCode);

    /**
     * 在指定字典类型下创建字典项。
     *
     * @param typeId 类型主键
     * @param command 创建命令
     * @param actor 操作人
     * @return 新字典项主键
     */
    long createDictionaryItem(
            @Param("typeId") long typeId,
            @Param("command") CreateDictionaryItemCommand command,
            @Param("actor") String actor
    );

    /**
     * 使用行版本更新系统字典项。
     *
     * @param itemId 字典项主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改行数
     */
    int updateDictionaryItem(
            @Param("itemId") long itemId,
            @Param("command") UpdateDictionaryItemCommand command,
            @Param("actor") String actor
    );

    /**
     * 统计数据库中通过外键实际引用该字典项的业务记录数量。
     *
     * @param itemId 字典项主键
     * @return 数据库中通过外键实际引用该字典项的业务记录数量
     */
    long countDictionaryItemReferences(long itemId);

    /**
     * 删除未被业务外键引用的系统字典项。
     *
     * @param itemId 字典项主键
     * @param expectedVersion 当前并发版本
     * @return 删除行数
     */
    int deleteDictionaryItem(@Param("itemId") long itemId, @Param("expectedVersion") byte[] expectedVersion);

    /**
     * 查询稳定排序外部系统。
     *
     * @return 稳定排序外部系统
     */
    List<ExternalSystem> findExternalSystems();

    /**
     * 查询外部系统。
     *
     * @param systemId 主键
     * @return 外部系统
     */
    Optional<ExternalSystem> findExternalSystem(long systemId);

    /**
     * 判断外部系统稳定代码是否已被占用。
     *
     * @param systemCode 稳定代码
     * @return 是否存在
     */
    boolean externalSystemCodeExists(String systemCode);

    /**
     * 创建外部系统登记信息。
     *
     * @param command 创建命令
     * @param actor 操作人
     * @return 新主键
     */
    long createExternalSystem(@Param("command") CreateExternalSystemRequest command, @Param("actor") String actor);

    /**
     * 使用行版本更新外部系统登记信息。
     *
     * @param systemId 主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改行数
     */
    int updateExternalSystem(@Param("systemId") long systemId,
                             @Param("command") UpdateExternalSystemRequest command,
                             @Param("actor") String actor);

    /**
     * 查询稳定排序服务地址。
     *
     * @param systemId 系统主键
     * @param organizationCodes 调用人获准访问的机构代码；空列表仅返回全局端点
     * @return 范围内稳定排序的服务地址
     */
    List<ExternalEndpoint> findExternalEndpoints(@Param("systemId") long systemId,
                                                  @Param("organizationCodes") List<String> organizationCodes);

    /**
     * 查询服务地址。
     *
     * @param endpointId 主键
     * @param organizationCodes 获准机构代码；全局端点不受机构筛选限制
     * @return 服务地址
     */
    Optional<ExternalEndpoint> findExternalEndpoint(@Param("endpointId") long endpointId,
                                                    @Param("organizationCodes") List<String> organizationCodes);

    /**
     * 查询当前机构在指定环境下启用的服务地址；不存在时为空。
     *
     * @param systemCode 外部系统稳定代码
     * @param environment 部署环境
     * @param organizationId 机构主键
     * @return 当前机构在指定环境下启用的服务地址；不存在时为空
     */
    Optional<ExternalEndpoint> findEnabledExternalEndpoint(
            @Param("systemCode") String systemCode,
            @Param("environment") ParameterEnvironment environment,
            @Param("organizationId") long organizationId
    );

    /**
     * 解析已保存且凭证可用的端点，供100-008自动校验使用；不要求端点已经启用。
     *
     * @param systemCode 外部系统稳定代码
     * @param environment 部署环境
     * @param organizationId 平台机构主键
     * @return 当前机构的待验证或已验证端点；不存在时为空
     */
    Optional<ExternalEndpoint> findConfiguredExternalEndpoint(
            @Param("systemCode") String systemCode,
            @Param("environment") ParameterEnvironment environment,
            @Param("organizationId") long organizationId
    );

    /**
     * 查询已启用且已配置认证信息的机构接口范围。
     *
     * @param systemCode 外部系统稳定代码
     * @param organizationCodes 当前操作人可访问的机构代码
     * @return 已启用且已配置认证信息的机构接口范围
     */
    List<ExternalEndpointScope> findEnabledExternalEndpointScopes(
            @Param("systemCode") String systemCode,
            @Param("organizationCodes") List<String> organizationCodes
    );

    /**
     * 判断同一系统、环境和机构范围是否已有端点。
     *
     * @param systemId 系统主键
     * @param excludedEndpointId 修改时排除的当前配置主键
     * @param environment 配置使用环境
     * @param organizationId 可选机构主键；为空时只匹配全局配置
     * @return 是否存在
     */
    boolean externalEndpointScopeExists(@Param("systemId") long systemId,
                                        @Param("excludedEndpointId") Long excludedEndpointId,
                                        @Param("environment") ParameterEnvironment environment,
                                        @Param("organizationId") Long organizationId);

    /**
     * 创建尚未投入业务运行的外部系统端点。
     *
     * @param systemId 系统主键
     * @param command 创建命令
     * @param actor 操作人
     * @return 新主键
     */
    long createExternalEndpoint(@Param("systemId") long systemId,
                                @Param("command") CreateExternalEndpointRequest command,
                                @Param("actor") String actor);

    /**
     * 使用行版本更新端点并使旧验证结果失效。
     *
     * @param endpointId 主键
     * @param command 修改命令
     * @param actor 操作人
     * @return 修改行数
     */
    int updateExternalEndpoint(@Param("endpointId") long endpointId,
                               @Param("command") UpdateExternalEndpointRequest command,
                               @Param("actor") String actor);

    /**
     * 保存100-008返回的唯一来源机构并将端点设为可运行。
     *
     * @param endpointId 端点主键
     * @param sourceOrganizationId HIS返回机构ID
     * @param sourceOrganizationName HIS返回机构名称
     * @param actor 操作人
     * @param expectedVersion 本次校验开始前读取的配置版本
     * @return 更新行数
     */
    int markExternalEndpointVerified(
            @Param("endpointId") long endpointId,
            @Param("sourceOrganizationId") String sourceOrganizationId,
            @Param("sourceOrganizationName") String sourceOrganizationName,
            @Param("expectedVersion") byte[] expectedVersion,
            @Param("actor") String actor
    );

    /**
     * 保存100-008明确失败或结果未知的事实并保持端点不可运行。
     *
     * @param endpointId 端点主键
     * @param verificationStatus 失败或结果未知状态
     * @param failureSummary 不包含地址、凭证和报文的提示
     * @param actor 操作人
     * @param expectedVersion 本次校验开始前读取的配置版本
     * @return 更新行数
     */
    int markExternalEndpointVerificationFailed(
            @Param("endpointId") long endpointId,
            @Param("verificationStatus") String verificationStatus,
            @Param("failureSummary") String failureSummary,
            @Param("expectedVersion") byte[] expectedVersion,
            @Param("actor") String actor
    );

    /**
     * 查询已保存认证密文；不存在时为空。
     *
     * @param endpointId 服务地址主键
     * @return 已保存认证密文；不存在时为空
     */
    Optional<ExternalEndpointCredential> findExternalEndpointCredential(long endpointId);

    /**
     * 为端点写入独立托管的认证密文。
     *
     * @param endpointId 服务地址主键
     * @param credential 认证密文
     * @param actor 操作人
     */
    void createExternalEndpointCredential(
            @Param("endpointId") long endpointId,
            @Param("credential") EncryptedExternalEndpointCredential credential,
            @Param("actor") String actor
    );

    /**
     * 替换端点现有的认证密文。
     *
     * @param endpointId 服务地址主键
     * @param credential 新认证密文
     * @param actor 操作人
     * @return 修改行数
     */
    int updateExternalEndpointCredential(
            @Param("endpointId") long endpointId,
            @Param("credential") EncryptedExternalEndpointCredential credential,
            @Param("actor") String actor
    );
}
