import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ForbiddenState } from '@cim/ui';

/**
 * 403：已登录但当前账号没有该页面的权限码。
 *
 * <p>路由级权限门（`App.tsx` 的 `PermGate`）在无权限时跳到这里，URL 变化可分享、可后退——
 * 与「就地渲染一个空壳页」相比，用户更容易判断是「权限问题」还是「数据没了」。</p>
 */
export default function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <ForbiddenState titleKey="mds.forbidden" hintKey="mds.forbiddenDesc">
      <Link className="btn" to="/">
        {t('mds.common.backHome')}
      </Link>
    </ForbiddenState>
  );
}
