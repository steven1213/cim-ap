import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ForbiddenState } from '@cim/ui';

/**
 * 403：已登录但无该页权限。
 *
 * <p>触发场景有两类：① 菜单被授权但对应权限码未授予（`sys_role_menu` 与 `sys_role_perm`
 * 是两套授权，允许交叉）；② 直接手输地址访问未授权路由。前端守卫只是体验层，
 * 后端 `@PreAuthorize` 才是最终判据。</p>
 *
 * <p>呈现用 `@cim/ui` 的 `ForbiddenState`（与 RMS 同一份壳子，图标/标题/说明/出口四段齐全）。
 * IAM 与 RMS 的差异只在**呈现位置**：本应用跳转 {@code /403}（让地址栏明确「你在 403」，
 * 便于引导去角色授权），RMS 就地渲染。见 `RequirePerm`。</p>
 */
export default function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <ForbiddenState titleKey="iam.common.forbidden.title" hintKey="iam.common.forbidden.desc">
      <Link className="btn" to="/">
        {t('iam.common.backHome')}
      </Link>
    </ForbiddenState>
  );
}
