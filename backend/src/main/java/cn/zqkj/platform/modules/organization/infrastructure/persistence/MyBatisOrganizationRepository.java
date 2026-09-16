package cn.zqkj.platform.modules.organization.infrastructure.persistence;

import cn.zqkj.platform.modules.organization.application.CreateOrganizationCommand;
import cn.zqkj.platform.modules.organization.application.OrganizationRepository;
import cn.zqkj.platform.modules.organization.application.OrganizationView;
import cn.zqkj.platform.modules.organization.application.UpdateOrganizationCommand;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 使用MyBatis实现平台机构持久化边界。
 */
@Repository
public class MyBatisOrganizationRepository implements OrganizationRepository {

    private final OrganizationMapper mapper;

    /**
     * 创建MyBatis机构仓储。
     *
     * @param mapper 机构MyBatis Mapper
     */
    public MyBatisOrganizationRepository(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<OrganizationView> findById(long id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    /** {@inheritDoc} */
    @Override
    public boolean existsByCode(String organizationCode) {
        return mapper.countByCode(organizationCode) > 0;
    }

    /** {@inheritDoc} */
    @Override
    public List<OrganizationView> findAll(Boolean enabled) {
        return mapper.findAll(enabled);
    }

    /** {@inheritDoc} */
    @Override
    public OrganizationView create(CreateOrganizationCommand command, String actor) {
        OrganizationInsertRow row = new OrganizationInsertRow(command, actor);
        mapper.insert(row);
        return mapper.findById(row.getId());
    }

    /** {@inheritDoc} */
    @Override
    public int update(long id, UpdateOrganizationCommand command, String actor) {
        return mapper.update(id, command, actor);
    }

    /** {@inheritDoc} */
    @Override
    public int setEnabled(long id, boolean enabled, byte[] expectedVersion, String actor) {
        return mapper.setEnabled(id, enabled, expectedVersion, actor);
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasEnabledChildren(long parentId) {
        return mapper.countEnabledChildren(parentId) > 0;
    }
}
