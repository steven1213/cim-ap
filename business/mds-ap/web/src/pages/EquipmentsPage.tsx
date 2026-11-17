// 设备页 = 设备列表的工艺视图（PROCESS 等非 AMHS 类）。见 TransportEquipmentsPage 说明。
import EquipmentListPage from './EquipmentListPage';

export default function EquipmentsPage() {
  return <EquipmentListPage view="process" />;
}
