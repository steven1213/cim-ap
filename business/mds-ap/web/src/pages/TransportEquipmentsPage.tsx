// 搬运设备页 = 设备列表的 AMHS 视图（TRANSPORT/STORAGE/BUFFER 类）。
//
// 为什么薄封装一层而不是继续在 App.tsx 里包内联组件：动态路由的组件注册表
// （import.meta.glob）按「文件 basename ↔ 菜单 component」解析，每个菜单 path
// 必须对应一个真实页面文件。视图参数（view=amhs）在这里固化，菜单数据不携带 props。
import EquipmentListPage from './EquipmentListPage';

export default function TransportEquipmentsPage() {
  return <EquipmentListPage view="amhs" />;
}
