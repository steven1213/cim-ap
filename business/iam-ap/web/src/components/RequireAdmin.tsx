import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { isIamAdmin } from '@/lib/permissions';

/**
 * 管理页路由守卫：仅 IAM 管理员可进入，否则重定向到概览。
 *
 * <p>说明：前端守卫只负责「体验」（不显示无权限入口）；真正的鉴权始终由后端
 * {@code @PreAuthorize("hasAuthority('iam-ap:ADMIN')")} 执行。</p>
 */
export default function RequireAdmin({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const user = useAuthStore((s) => s.user);

  if (!token) return <Navigate to="/login" replace />;
  // 令牌已在但用户信息尚未加载：先等待（Layout 会触发 loadMe）
  if (!user) {
    return (
      <div className="panel">
        <div className="panel-body">
          <div className="empty">
            <b>正在校验权限…</b>
          </div>
        </div>
      </div>
    );
  }
  if (!isIamAdmin(user)) return <Navigate to="/" replace />;
  return <>{children}</>;
}
