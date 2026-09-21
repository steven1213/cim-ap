import { createBrowserRouter } from 'react-router-dom';

// 路由骨架：动态路由 + 守卫（平台前端文档 §3）。当前为占位，后续接入 IAM 准入守卫。
export const router = createBrowserRouter([
  {
    path: '/',
    element: <div>CIM-AP</div>,
  },
]);

export default router;
