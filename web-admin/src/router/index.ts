import { createRouter, createWebHistory } from 'vue-router';

const Layout = () => import('@/layout/AppLayout.vue');
const PlaceholderView = () => import('@/views/error/PlaceholderView.vue');
const DashboardView = () => import('@/views/dashboard/DashboardView.vue');
const OrganizationsView = () =>
  import('@/views/system/organization/OrganizationView.vue');
const PrototypeView = () => import('@/views/prototype/PrototypeView.vue');
const Icd10DirectoryPrototypeView = () =>
  import('@/views/prototype/Icd10DirectoryPrototypeView.vue');
const DatabaseContractPrototypeView = () =>
  import('@/views/prototype/DatabaseContractPrototypeView.vue');
const DatabaseContractMaintenanceView = () =>
  import('@/views/database-contract/DatabaseContractMaintenanceView.vue');
const AccessDeniedView = () => import('@/views/error/AccessDeniedView.vue');
const NotFoundView = () => import('@/views/error/NotFoundView.vue');
const ChangePasswordView = () => import('@/views/login/ChangePasswordView.vue');
const LoginView = () => import('@/views/login/LoginView.vue');
const UsersView = () => import('@/views/system/user/UserView.vue');
const RolesView = () => import('@/views/system/role/RoleView.vue');
const ParametersView = () =>
  import('@/views/configuration/parameter/ParameterView.vue');
const DictionariesView = () =>
  import('@/views/configuration/dictionary/DictionaryView.vue');
const ExternalSystemsView = () =>
  import('@/views/configuration/external-system/ExternalSystemView.vue');
const AuditView = () => import('@/views/audit/management/AuditView.vue');
const HospitalDirectoryView = () =>
  import('@/views/master-data/directory/HospitalDirectoryView.vue');
const MedicalDirectoryView = () =>
  import('@/views/master-data/directory/MedicalDirectoryView.vue');
const MasterDataBatchView = () =>
  import('@/views/master-data/batch/MasterDataBatchView.vue');
const MasterDataBatchDetailView = () =>
  import('@/views/master-data/batch/MasterDataBatchDetailView.vue');

/** 管理端页面地址表；正式业务页面统一挂载在登录后布局下。 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      component: LoginView,
      meta: { title: '登录平台', public: true, guestOnly: true },
    },
    {
      path: '/change-password',
      component: ChangePasswordView,
      meta: { title: '修改初始密码', public: false },
    },
    {
      path: '/forbidden',
      component: AccessDeniedView,
      meta: { title: '无权访问', public: false },
    },
    {
      path: '/prototype',
      component: PrototypeView,
      meta: { title: '业务原型', public: true },
    },
    {
      path: '/prototype/database-contract',
      component: DatabaseContractPrototypeView,
      meta: { title: '数据库契约维护原型', public: true },
    },
    {
      path: '/',
      component: Layout,
      children: [
        // 未完成功能的页面地址保留直达占位提示，但不会进入正式侧栏导航。
        {
          path: '',
          component: DashboardView,
          meta: { title: '运行总览', public: false },
        },
        {
          path: 'organizations',
          component: OrganizationsView,
          meta: {
            title: '机构管理',
            public: false,
            requiredPermission: 'organization:read',
          },
        },
        {
          path: 'users',
          component: UsersView,
          meta: {
            title: '用户管理',
            public: false,
            requiredPermission: 'identity:read',
          },
        },
        {
          path: 'roles',
          component: RolesView,
          meta: {
            title: '角色权限',
            public: false,
            requiredPermission: 'access:read',
          },
        },
        {
          path: 'parameters',
          component: ParametersView,
          meta: {
            title: '参数配置',
            public: false,
            requiredPermission: 'configuration:read',
          },
        },
        {
          path: 'dictionaries',
          component: DictionariesView,
          meta: {
            title: '数据字典',
            public: false,
            requiredPermission: 'configuration:read',
          },
        },
        {
          path: 'external-systems',
          component: ExternalSystemsView,
          meta: {
            title: '外部系统',
            public: false,
            requiredPermission: 'configuration:read',
          },
        },
        {
          path: 'database-contract',
          component: DatabaseContractMaintenanceView,
          meta: {
            title: '数据库契约维护',
            public: false,
            requiredPermission: 'database-contract:read',
          },
        },
        {
          path: 'master-data/directory',
          component: HospitalDirectoryView,
          meta: {
            title: '数据目录',
            public: false,
            requiredPermission: 'master-data:read',
          },
        },
        {
          path: 'master-data/directory/medical',
          component: MedicalDirectoryView,
          meta: {
            title: '数据目录',
            public: false,
            requiredPermission: 'master-data:read',
          },
        },
        {
          path: 'master-data/directory/icd10',
          component: Icd10DirectoryPrototypeView,
          meta: {
            title: '数据目录',
            public: false,
            requiredPermission: 'master-data:read',
            prototypeOnly: true,
            catalogScope: 'PUBLIC',
          },
        },
        {
          path: 'master-data/batches',
          component: MasterDataBatchView,
          meta: {
            title: '同步批次',
            public: false,
            requiredPermission: 'master-data:read',
          },
        },
        {
          path: 'master-data/batches/:id',
          component: MasterDataBatchDetailView,
          meta: {
            title: '批次详情',
            public: false,
            requiredPermission: 'master-data:read',
          },
        },
        {
          path: 'master-data/organization-mappings',
          redirect: '/external-systems',
        },
        {
          path: 'interfaces',
          component: PlaceholderView,
          meta: { title: '接口与映射', public: false },
        },
        {
          path: 'exchanges',
          component: PlaceholderView,
          meta: { title: '交换记录', public: false },
        },
        {
          path: 'exceptions',
          component: PlaceholderView,
          meta: { title: '异常与处理', public: false },
        },
        { path: 'data-review', redirect: '/master-data/batches' },
        {
          path: 'audit',
          component: AuditView,
          meta: {
            title: '审计记录',
            public: false,
            requiredPermission: 'audit:read',
          },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      component: NotFoundView,
      meta: { title: '页面不存在', public: true },
    },
  ],
});

export default router;
