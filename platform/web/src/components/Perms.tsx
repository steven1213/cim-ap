import type { ReactNode } from 'react';

interface PermsProps {
  /** 所需权限标识；后续接入本 ap 的 RBAC 判定（平台前端文档 §4） */
  permission?: string;
  children: ReactNode;
}

// 权限组件骨架：无权限时「不渲染」（非 display:none，平台 §4 强制约束）。
// 判定链路：IAM 准入（apps claim）→ 本 ap 业务 RBAC。
export default function Perms({ children }: PermsProps) {
  // TODO: 接入 IAM 准入 + 业务 RBAC 判定
  return <>{children}</>;
}
