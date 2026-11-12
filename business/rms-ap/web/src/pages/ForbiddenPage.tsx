import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ForbiddenState } from '@cim/ui';

/**
 * 403：已登录但当前账号无该页权限。
 *
 * <p>形态与 IAM 的是同一个组件（`@cim/ui` 的 `ForbiddenState`）——此前这里只有一行
 * 「403 / 无访问权限」，没有图标也没有出口，和 IAM 那份（图标 + 说明 + 返回首页）
 * 完全不像同一个产品。文案仍各自提供，壳子共用。</p>
 *
 * <p>与 IAM 唯一保留的差异是**呈现位置**：RMS 就地渲染（URL 不变，权限补上后刷新即达原页），
 * IAM 跳转 {@code /403}（权限管理台要明确「你在 403」才能引导去角色授权）。见 `RequirePerm`。</p>
 */
export default function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <ForbiddenState title="403" hintKey="rms.forbidden">
      <Link className="btn" to="/">
        {t('rms.common.backHome')}
      </Link>
    </ForbiddenState>
  );
}
