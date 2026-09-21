import { RouterProvider } from 'react-router-dom';
import { router } from './router';

// 应用根组件：挂载路由。布局 / 守卫在 router 中组合（平台前端文档 §1/§3）。
export default function App() {
  return <RouterProvider router={router} />;
}
