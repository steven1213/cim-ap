// IAM 组织节点 → 树形下拉选项（FormTreeOption）。
//
// 部门下拉统一口径（V36 起部门真源 = IAM 组织架构）：
//   - 整棵组织树作层级上下文展示（制造维度节点可见但不可选）；
//   - 仅 `nodeType=DEPT` 且启用的节点可选（与后端 requireDeptCode 守门同口径）；
//   - 父节点缺失（目录变更）时按根挂载，不丢节点。

import type { FormTreeOption } from '@cim/ui';
import type { OrgNodeOption } from './mdsApi';

/** 部门下拉是否可选：启用 DEPT 节点。 */
export function isDeptSelectable(n: OrgNodeOption): boolean {
  return n.nodeType === 'DEPT' && n.enabled;
}

/** 组织节点列表 → 树形选项（label 带负责人后缀，hint 为编码，参与搜索命中）。 */
export function buildOrgTreeOptions(nodes: OrgNodeOption[]): FormTreeOption[] {
  type Opt = FormTreeOption & { children: FormTreeOption[] };
  const byCode = new Map<string, Opt>();
  const made: Opt[] = nodes.map((n) => {
    const opt: Opt = {
      value: n.code,
      label: n.leaderName ? `${n.name} · ${n.leaderName}` : n.name,
      hint: n.code,
      disabled: !isDeptSelectable(n),
      children: [],
    };
    byCode.set(n.code, opt);
    return opt;
  });
  const roots: Opt[] = [];
  for (const opt of made) {
    const parent = nodes.find((n) => n.code === opt.value)?.parentCode ?? null;
    const parentOpt = parent ? byCode.get(parent) : undefined;
    (parentOpt ?? { children: roots }).children.push(opt);
  }
  return roots;
}
