import { useTranslation } from 'react-i18next';

/**
 * 占位页：菜单已入库、路由已动态挂载，但页面实现尚未落地时的兜底展示。
 *
 * <p>存在的意义是让「配置化菜单」端到端可用——管理员在「菜单管理」里新增一个菜单并授权后，
 * 侧栏与路由**立刻**生效，不会因前端缺少对应组件而白屏。</p>
 */
export default function ComingSoon({ titleKey, descKey }: { titleKey?: string; descKey?: string }) {
  const { t } = useTranslation();
  return (
    <div className="panel">
      <div className="panel-head">
        <h2>{titleKey ? t(titleKey) : t('iam.common.loading')}</h2>
      </div>
      <div className="panel-body">
        <div className="empty">
          <b>{t('iam.common.wip')}</b>
          {descKey && <p>{t(descKey)}</p>}
        </div>
      </div>
    </div>
  );
}
