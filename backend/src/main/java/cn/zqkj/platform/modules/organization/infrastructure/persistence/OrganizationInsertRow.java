package cn.zqkj.platform.modules.organization.infrastructure.persistence;

import cn.zqkj.platform.modules.organization.application.CreateOrganizationCommand;

import java.time.LocalDateTime;

/**
 * 承载MyBatis机构新建参数和SQL Server生成的主键。
 */
public class OrganizationInsertRow {

    private Long id;
    private final String organizationCode;
    private final String organizationName;
    private final String organizationType;
    private final Long parentId;
    private final LocalDateTime validFrom;
    private final LocalDateTime validTo;
    private final String actor;

    /**
     * 根据已校验命令创建持久化参数。
     *
     * @param command 创建机构命令
     * @param actor 操作主体标识
     */
    public OrganizationInsertRow(CreateOrganizationCommand command, String actor) {
        this.organizationCode = command.organizationCode();
        this.organizationName = command.organizationName();
        this.organizationType = command.organizationType();
        this.parentId = command.parentId();
        this.validFrom = command.validFrom();
        this.validTo = command.validTo();
        this.actor = actor;
    }

    /**
     * 读取SQL Server生成的机构主键。
     *
     * @return 机构主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 回填SQL Server生成的机构主键。
     *
     * @param id 机构主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return 机构编码 */
    public String getOrganizationCode() {
        return organizationCode;
    }

    /** @return 机构名称 */
    public String getOrganizationName() {
        return organizationName;
    }

    /** @return 机构类型代码 */
    public String getOrganizationType() {
        return organizationType;
    }

    /** @return 可选父机构主键 */
    public Long getParentId() {
        return parentId;
    }

    /** @return 可选有效起始UTC时间 */
    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    /** @return 可选有效结束UTC时间 */
    public LocalDateTime getValidTo() {
        return validTo;
    }

    /** @return 操作主体标识 */
    public String getActor() {
        return actor;
    }
}
