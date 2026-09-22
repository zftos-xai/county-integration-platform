package cn.zqkj.platform.system.organization.domain.dto;

import cn.zqkj.platform.common.utils.Func;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 定义创建平台机构的机构维护输入。
 *
 * <p>写入目标：{@code dbo.org_organization}。业务说明：维护平台机构档案和上下级关系；此输入不包含主键、启用状态及审计列。</p>
 *
 * @param organizationCode 平台统一机构编码
 * @param organizationName 机构名称
 * @param organizationType 经确认的机构类型代码
 * @param parentId 可选父机构主键
 * @param validFrom 可选有效起始时间
 * @param validTo 可选有效结束时间
 */
public record CreateOrganizationCommand(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+") String organizationCode,
        @NotBlank @Size(max = 200) String organizationName,
        @NotBlank @Size(max = 32) String organizationType,
        @Positive Long parentId,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
) {
    /**
     * 统一机构资料文本，时间仍保留请求偏移量供入口校验。
     *
     * @param organizationCode 平台统一机构编码
     * @param organizationName 机构名称
     * @param organizationType 经确认的机构类型代码
     * @param parentId 可选父机构主键
     * @param validFrom 可选有效起始时间
     * @param validTo 可选有效结束时间
     */
    public CreateOrganizationCommand {
        organizationCode = Func.trimToNull(organizationCode);
        organizationName = Func.trimToNull(organizationName);
        organizationType = Func.trimToNull(organizationType);
    }

    /**
     * 检查机构有效期是否倒置。
     * @return 未限定区间或结束时间不早于起始时间时为true
     */
    @AssertTrue(message = "有效结束时间不能早于起始时间")
    public boolean isTimeRangeValid() {
        return validFrom == null || validTo == null || !validTo.isBefore(validFrom);
    }

    /**
     * 为数据库写入提供有效期起始UTC时间。
     * @return UTC时间；未限定时为空
     */
    public LocalDateTime validFromUtc() {
        return Func.toUtc(validFrom);
    }

    /**
     * 为数据库写入提供有效期结束UTC时间。
     * @return UTC时间；未限定时为空
     */
    public LocalDateTime validToUtc() {
        return Func.toUtc(validTo);
    }
}
