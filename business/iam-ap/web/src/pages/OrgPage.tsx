import { useEffect, useMemo, useState, type FormEvent } from 'react';
import * as api from '@/lib/api';
import type { AdminUserRow, OrgNode, OrgNodeType } from '@/types';
import { fmtRelative, orgTypeLabel, originLabel } from '@/lib/format';
import Panel from '@/components/Panel';
import Perms from '@/components/Perms';
import { ORG_CREATE, ORG_DELETE, ORG_MEMBER, ORG_MOVE, ORG_UPDATE } from '@/lib/permCodes';
import {
  IconAlert,
  IconChevron,
  IconCheckCircle,
  IconEdit,
  IconInbox,
  IconPlus,
  IconRefresh,
  IconTree,
  IconTrash,
} from '@/components/Icons';

/**
 * 组织架构（制造组织树）。
 *
 * <p>厂区 → 车间 → 产线 → 工序 → 班组由 IAM 自建；AD 同步来的行政部门为只读。
 * 组织是「按组织批量授权」与数据权限的上游——先把树建对，再挂人与授权。
 * 各写操作按 `iam:org:*` 权限显隐。</p>
 */

interface TreeNode {
  node: OrgNode;
  children: TreeNode[];
}

function buildTree(nodes: OrgNode[]): TreeNode[] {
  const byId = new Map<string, TreeNode>();
  nodes.forEach((n) => byId.set(n.id, { node: n, children: [] }));
  const roots: TreeNode[] = [];
  byId.forEach((t) => {
    if (t.node.parentId && byId.has(t.node.parentId)) {
      byId.get(t.node.parentId)!.children.push(t);
    } else {
      roots.push(t);
    }
  });
  const sort = (list: TreeNode[]) => {
    list.sort((a, b) => a.node.sortNo - b.node.sortNo || a.node.code.localeCompare(b.node.code));
    list.forEach((t) => sort(t.children));
  };
  sort(roots);
  return roots;
}

const CREATE_TYPES: OrgNodeType[] = ['AREA', 'WORKSHOP', 'LINE', 'PROCESS', 'TEAM'];

export default function OrgPage() {
  const [nodes, setNodes] = useState<OrgNode[]>([]);
  const [users, setUsers] = useState<AdminUserRow[]>([]);
  const [err, setErr] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [selected, setSelected] = useState<string | null>(null);
  const [members, setMembers] = useState<string[]>([]);

  // 新建
  const [showCreate, setShowCreate] = useState(false);
  const [parentId, setParentId] = useState('');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [nodeType, setNodeType] = useState<OrgNodeType>('WORKSHOP');
  const [sortNo, setSortNo] = useState(0);

  // 编辑
  const [editName, setEditName] = useState('');
  const [editSort, setEditSort] = useState(0);
  const [editStatus, setEditStatus] = useState<'ENABLED' | 'DISABLED'>('ENABLED');
  // 移动
  const [moveTo, setMoveTo] = useState('');
  // 挂人
  const [addUser, setAddUser] = useState('');

  const tree = useMemo(() => buildTree(nodes), [nodes]);
  const selectedNode = nodes.find((n) => n.id === selected) ?? null;
  const managed = selectedNode?.source === 'IAM_MANAGED';

  async function load() {
    setLoading(true);
    setErr('');
    try {
      const [orgs, us] = await Promise.all([
        api.get<OrgNode[]>('/admin/orgs'),
        api.get<AdminUserRow[]>('/admin/users'),
      ]);
      setNodes(orgs);
      setUsers(us);
      setExpanded((prev) => (prev.size ? prev : new Set(orgs.map((o) => o.id))));
    } catch (e: any) {
      setErr(e?.msg || '加载组织失败');
    } finally {
      setLoading(false);
    }
  }

  async function loadMembers(orgId: string | null) {
    if (!orgId) {
      setMembers([]);
      return;
    }
    try {
      setMembers(await api.get<string[]>(`/admin/orgs/${encodeURIComponent(orgId)}/users`));
    } catch {
      setMembers([]);
    }
  }

  useEffect(() => {
    load();
  }, []);

  useEffect(() => {
    loadMembers(selected);
    if (selectedNode) {
      setEditName(selectedNode.name);
      setEditSort(selectedNode.sortNo);
      setEditStatus(selectedNode.status);
      setMoveTo(selectedNode.parentId ?? '');
    }
  }, [selected, nodes.length]);

  function flash(t: string) {
    setMsg(t);
    setTimeout(() => setMsg(''), 3000);
  }

  function toggle(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    setErr('');
    setBusy(true);
    try {
      await api.post('/admin/orgs', {
        parentId: parentId || null,
        code: code.trim(),
        name: name.trim(),
        nodeType,
        sortNo: Number(sortNo) || 0,
      });
      flash(`已新建组织 ${name}`);
      setCode('');
      setName('');
      setShowCreate(false);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '新建失败');
    } finally {
      setBusy(false);
    }
  }

  async function onUpdate(e: FormEvent) {
    e.preventDefault();
    if (!selected) return;
    setErr('');
    setBusy(true);
    try {
      await api.put(`/admin/orgs/${encodeURIComponent(selected)}`, {
        name: editName.trim(),
        sortNo: Number(editSort) || 0,
        status: editStatus,
      });
      flash('已保存组织信息');
      await load();
    } catch (e: any) {
      setErr(e?.msg || '保存失败');
    } finally {
      setBusy(false);
    }
  }

  async function onMove() {
    if (!selected) return;
    setErr('');
    try {
      await api.put(`/admin/orgs/${encodeURIComponent(selected)}/move`, { newParentId: moveTo || null });
      flash('已移动组织（子树路径已重写，相关用户令牌已失效需重登）');
      await load();
    } catch (e: any) {
      setErr(e?.msg || '移动失败');
    }
  }

  async function onDelete() {
    if (!selectedNode) return;
    if (!window.confirm(`确认删除组织「${selectedNode.name}」？须无子节点、无人员归属、无组织授予。`)) return;
    setErr('');
    try {
      await api.del(`/admin/orgs/${encodeURIComponent(selectedNode.id)}`);
      flash('已删除组织');
      setSelected(null);
      await load();
    } catch (e: any) {
      setErr(e?.msg || '删除失败');
    }
  }

  async function onAddMember() {
    if (!selected || !addUser.trim()) return;
    setErr('');
    try {
      const uid = addUser.trim();
      const cur = await api.get<{ orgId: string; primary: boolean }[]>(
        `/admin/users/${encodeURIComponent(uid)}/orgs`,
      );
      if (cur.some((o) => o.orgId === selected)) {
        flash(`${uid} 已归属该组织`);
        return;
      }
      const next = [...cur.map((o) => ({ orgId: o.orgId, primary: o.primary })),
        { orgId: selected, primary: cur.length === 0 }];
      await api.put(`/admin/users/${encodeURIComponent(uid)}/orgs`, { orgs: next });
      flash(`已把 ${uid} 挂到该组织`);
      setAddUser('');
      await loadMembers(selected);
    } catch (e: any) {
      setErr(e?.msg || '挂人失败');
    }
  }

  async function onRemoveMember(uid: string) {
    if (!selected) return;
    setErr('');
    try {
      const cur = await api.get<{ orgId: string; primary: boolean }[]>(
        `/admin/users/${encodeURIComponent(uid)}/orgs`,
      );
      const next = cur.filter((o) => o.orgId !== selected).map((o) => ({ orgId: o.orgId, primary: o.primary }));
      await api.put(`/admin/users/${encodeURIComponent(uid)}/orgs`, { orgs: next });
      flash(`已从该组织移除 ${uid}`);
      await loadMembers(selected);
    } catch (e: any) {
      setErr(e?.msg || '移除失败');
    }
  }

  function renderTree(list: TreeNode[], depth: number) {
    return list.map((t) => {
      const hasChildren = t.children.length > 0;
      const open = expanded.has(t.node.id);
      return (
        <div key={t.node.id}>
          <div
            className={'tree-row' + (selected === t.node.id ? ' on' : '')}
            style={{ paddingLeft: 8 + depth * 17 }}
            onClick={() => setSelected(t.node.id)}
          >
            <button
              type="button"
              className={'tree-toggle' + (hasChildren ? '' : ' hollow') + (open ? ' open' : '')}
              onClick={(e) => {
                e.stopPropagation();
                if (hasChildren) toggle(t.node.id);
              }}
              aria-label={open ? '折叠' : '展开'}
            >
              <IconChevron width={13} height={13} />
            </button>
            <span className="tree-name">{t.node.name}</span>
            <span className="mono dim tree-code">{t.node.code}</span>
            <span className="tag">{orgTypeLabel(t.node.nodeType)}</span>
            {t.node.source === 'AD_SYNCED' && <span className="tag info">AD</span>}
            {t.node.status === 'DISABLED' && <span className="tag err">停用</span>}
          </div>
          {hasChildren && open && renderTree(t.children, depth + 1)}
        </div>
      );
    });
  }

  const flatOptions = nodes
    .slice()
    .sort((a, b) => a.path.localeCompare(b.path))
    .map((n) => ({
      id: n.id,
      label: `${'·'.repeat(Math.max(0, n.path.split('/').filter(Boolean).length - 1))} ${n.name} (${n.code})`,
    }));

  return (
    <div className="page">
      {err && (
        <div className="alert err">
          <IconAlert width={15} height={15} />
          <span>{err}</span>
        </div>
      )}
      {msg && (
        <div className="alert ok">
          <IconCheckCircle width={15} height={15} />
          <span>{msg}</span>
        </div>
      )}

      <div className="split">
        <Panel
          title="组织树"
          sub="AD 同步的行政部门只读；制造组织由 IAM 自建"
          flush
          actions={
            <>
              <span className="tag">{nodes.length} 节点</span>
              <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
                <IconRefresh width={13} height={13} />
                {loading ? '刷新中' : '刷新'}
              </button>
              <Perms code={ORG_CREATE}>
                <button className="btn-sm" onClick={() => setShowCreate((v) => !v)}>
                  <IconPlus width={13} height={13} />
                  新建节点
                </button>
              </Perms>
            </>
          }
        >
          {tree.length ? (
            <div className="tree">{renderTree(tree, 0)}</div>
          ) : (
            <div className="empty">
              <IconTree width={22} height={22} />
              <b>暂无组织节点</b>
              <span>点击右上「新建节点」建立第一个厂区</span>
            </div>
          )}
        </Panel>

        <Panel
          title={selectedNode ? `节点 · ${selectedNode.name}` : '节点详情'}
          sub={selectedNode ? `${originLabel(selectedNode.source)}${managed ? '' : '（只读）'}` : '在左侧选择一个节点'}
        >
          {!selectedNode ? (
            <div className="empty">
              <IconInbox width={22} height={22} />
              <b>未选择节点</b>
              <span>选择左侧任一节点查看与维护</span>
            </div>
          ) : (
            <>
              <dl className="kv">
                <dt>编码</dt>
                <dd className="mono">{selectedNode.code}</dd>
                <dt>类型</dt>
                <dd>{orgTypeLabel(selectedNode.nodeType)}</dd>
                <dt>来源</dt>
                <dd>{originLabel(selectedNode.source)}</dd>
                <dt>状态</dt>
                <dd>
                  <span className="status">
                    <i className={'led ' + (selectedNode.status === 'ENABLED' ? 'ok' : 'err')} />
                    <b>{selectedNode.status === 'ENABLED' ? '启用' : '停用'}</b>
                  </span>
                </dd>
                <dt>物化路径</dt>
                <dd className="mono dim">{selectedNode.path}</dd>
                <dt>最近变更</dt>
                <dd>{fmtRelative(selectedNode.updatedAt)}</dd>
              </dl>

              {managed ? (
                <>
                  <form onSubmit={onUpdate} style={{ marginTop: 14 }}>
                    <div className="row">
                      <div className="field">
                        <label className="field-label" htmlFor="on">
                          名称
                        </label>
                        <input id="on" value={editName} onChange={(e) => setEditName(e.target.value)} />
                      </div>
                      <div className="field">
                        <label className="field-label" htmlFor="os">
                          排序
                        </label>
                        <input
                          id="os"
                          type="number"
                          className="mono"
                          value={editSort}
                          onChange={(e) => setEditSort(Number(e.target.value))}
                        />
                      </div>
                      <div className="field">
                        <label className="field-label" htmlFor="ost">
                          状态
                        </label>
                        <select
                          id="ost"
                          value={editStatus}
                          onChange={(e) => setEditStatus(e.target.value as 'ENABLED' | 'DISABLED')}
                        >
                          <option value="ENABLED">启用</option>
                          <option value="DISABLED">停用</option>
                        </select>
                      </div>
                      <Perms code={ORG_UPDATE}>
                        <button type="submit" className="btn-sm" disabled={busy}>
                          <IconEdit width={13} height={13} />
                          保存
                        </button>
                      </Perms>
                    </div>
                  </form>

                  <div className="row" style={{ marginTop: 12 }}>
                    <div className="field">
                      <label className="field-label" htmlFor="mv">
                        移动到父节点
                      </label>
                      <select id="mv" value={moveTo} onChange={(e) => setMoveTo(e.target.value)}>
                        <option value="">（无 / 作为根节点）</option>
                        {flatOptions
                          .filter((o) => o.id !== selectedNode.id)
                          .map((o) => (
                            <option key={o.id} value={o.id}>
                              {o.label}
                            </option>
                          ))}
                      </select>
                    </div>
                    <Perms code={ORG_MOVE}>
                      <button type="button" className="btn-ghost btn-sm" onClick={onMove}>
                        移动
                      </button>
                    </Perms>
                    <Perms code={ORG_DELETE}>
                      <button type="button" className="btn-danger btn-sm" onClick={onDelete}>
                        <IconTrash width={13} height={13} />
                        删除
                      </button>
                    </Perms>
                  </div>
                </>
              ) : (
                <p className="hint" style={{ marginTop: 12 }}>
                  该节点由 AD 同步而来，IAM 侧只读——请在企业目录（AD/HR）中维护，同步器只写自己那一份。
                </p>
              )}

              <div className="sub-block">
                <div className="sub-head">
                  <b>组织成员</b>
                  <span className="tag">{members.length} 人</span>
                </div>
                <div className="row">
                  <div className="field">
                    <input
                      className="mono"
                      list="all-users"
                      placeholder="输入或选择用户 ID"
                      value={addUser}
                      onChange={(e) => setAddUser(e.target.value)}
                    />
                    <datalist id="all-users">
                      {users.map((u) => (
                        <option key={u.userId} value={u.userId}>
                          {u.displayName || u.username}
                        </option>
                      ))}
                    </datalist>
                  </div>
                  <Perms code={ORG_MEMBER}>
                    <button type="button" className="btn-sm" onClick={onAddMember} disabled={!addUser.trim()}>
                      <IconPlus width={13} height={13} />
                      挂到该组织
                    </button>
                  </Perms>
                </div>
                <div className="tag-list" style={{ marginTop: 10 }}>
                  {members.map((m) => (
                    <span key={m} className="role-chip">
                      <span className="mono">{m}</span>
                      <Perms code={ORG_MEMBER}>
                        <button type="button" className="chip-x" onClick={() => onRemoveMember(m)} aria-label="移除">
                          ×
                        </button>
                      </Perms>
                    </span>
                  ))}
                  {!members.length && <span className="dim">暂无成员</span>}
                </div>
                <p className="hint">
                  成员归属支持<b>多归属</b>（一人可同属多个工序/产线）。归属变更会 bump 用户令牌版本，其会话立即失效。
                </p>
              </div>
            </>
          )}
        </Panel>
      </div>

      {showCreate && (
        <Panel title="新建组织节点" sub="IAM 自建（制造维度）；编码建议直接沿用 MES 侧既定编码，不另造第二套">
          <form onSubmit={onCreate}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="pp">
                  父节点
                </label>
                <select id="pp" value={parentId} onChange={(e) => setParentId(e.target.value)}>
                  <option value="">（无 / 作为根节点）</option>
                  {flatOptions.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label" htmlFor="cc">
                  编码
                </label>
                <input
                  id="cc"
                  className="mono"
                  placeholder="LINE-ETCH-01"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nn">
                  名称
                </label>
                <input id="nn" placeholder="蚀刻 1 线" value={name} onChange={(e) => setName(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nt">
                  类型
                </label>
                <select id="nt" value={nodeType} onChange={(e) => setNodeType(e.target.value as OrgNodeType)}>
                  {CREATE_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {orgTypeLabel(t)}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label" htmlFor="sn">
                  排序
                </label>
                <input
                  id="sn"
                  type="number"
                  className="mono"
                  value={sortNo}
                  onChange={(e) => setSortNo(Number(e.target.value))}
                />
              </div>
              <Perms code={ORG_CREATE}>
                <button type="submit" disabled={busy || !code.trim() || !name.trim()}>
                  {busy ? '创建中…' : '创建'}
                </button>
              </Perms>
            </div>
          </form>
          <p className="hint">
            组织是「按组织批量授权」与数据权限的上游：把树建对 → 挂人 → 在「组织授权」给组织授予 ap 与角色组。
          </p>
        </Panel>
      )}
    </div>
  );
}
