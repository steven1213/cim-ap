import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
      setErr(e?.msg || t('iam.orgs.errLoad'));
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
      flash(t('iam.orgs.msgCreated', { name }));
      setCode('');
      setName('');
      setShowCreate(false);
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgs.errCreate'));
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
      flash(t('iam.orgs.msgSaved'));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgs.errSave'));
    } finally {
      setBusy(false);
    }
  }

  async function onMove() {
    if (!selected) return;
    setErr('');
    try {
      await api.put(`/admin/orgs/${encodeURIComponent(selected)}/move`, { newParentId: moveTo || null });
      flash(t('iam.orgs.msgMoved'));
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgs.errMove'));
    }
  }

  async function onDelete() {
    if (!selectedNode) return;
    if (!window.confirm(t('iam.orgs.confirmDelete', { name: selectedNode.name }))) return;
    setErr('');
    try {
      await api.del(`/admin/orgs/${encodeURIComponent(selectedNode.id)}`);
      flash(t('iam.orgs.msgDeleted'));
      setSelected(null);
      await load();
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgs.errDelete'));
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
        flash(t('iam.orgs.msgMemberAdded', { uid }));
        return;
      }
      const next = [...cur.map((o) => ({ orgId: o.orgId, primary: o.primary })),
        { orgId: selected, primary: cur.length === 0 }];
      await api.put(`/admin/users/${encodeURIComponent(uid)}/orgs`, { orgs: next });
      flash(t('iam.orgs.msgMemberMoved', { uid }));
      setAddUser('');
      await loadMembers(selected);
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgs.errMember'));
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
      flash(t('iam.orgs.msgMemberRemoved', { uid }));
      await loadMembers(selected);
    } catch (e: any) {
      setErr(e?.msg || t('iam.orgs.errRemove'));
    }
  }

  function renderTree(list: TreeNode[], depth: number) {
    return list.map((node) => {
      const hasChildren = node.children.length > 0;
      const open = expanded.has(node.node.id);
      return (
        <div key={node.node.id}>
          <div
            className={'tree-row' + (selected === node.node.id ? ' on' : '')}
            style={{ paddingLeft: 8 + depth * 17 }}
            onClick={() => setSelected(node.node.id)}
          >
            <button
              type="button"
              className={'tree-toggle' + (hasChildren ? '' : ' hollow') + (open ? ' open' : '')}
              onClick={(e) => {
                e.stopPropagation();
                if (hasChildren) toggle(node.node.id);
              }}
              aria-label={open ? t('iam.orgs.collapse') : t('iam.orgs.expand')}
            >
              <IconChevron width={13} height={13} />
            </button>
            <span className="tree-name">{node.node.name}</span>
            <span className="mono dim tree-code">{node.node.code}</span>
            <span className="tag">{orgTypeLabel(node.node.nodeType)}</span>
            {node.node.source === 'AD_SYNCED' && <span className="tag info">AD</span>}
            {node.node.status === 'DISABLED' && <span className="tag err">{t('iam.orgs.statusDisabled')}</span>}
          </div>
          {hasChildren && open && renderTree(node.children, depth + 1)}
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
        title={t('iam.orgs.title')}
        sub={t('iam.orgs.sub')}
        flush
        actions={
          <>
            <span className="tag">{t('iam.orgs.count', { n: nodes.length })}</span>
            <button className="btn-ghost btn-sm" onClick={load} disabled={loading}>
              <IconRefresh width={13} height={13} />
              {loading ? t('iam.orgs.refreshing') : t('iam.orgs.refresh')}
            </button>
            <Perms code={ORG_CREATE}>
              <button className="btn-sm" onClick={() => setShowCreate((v) => !v)}>
                <IconPlus width={13} height={13} />
                {t('iam.orgs.newNode')}
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
              <b>{t('iam.orgs.emptyTitle')}</b>
              <span>{t('iam.orgs.emptyHint')}</span>
            </div>
          )}
        </Panel>

        <Panel
          title={selectedNode ? t('iam.orgs.nodeTitle', { name: selectedNode.name }) : t('iam.orgs.nodeDetail')}
          sub={selectedNode ? t('iam.orgs.nodeSub', { src: originLabel(selectedNode.source), ro: managed ? t('iam.orgs.nodeReadOnly') : '' }) : t('iam.orgs.selectNode')}
        >
          {!selectedNode ? (
            <div className="empty">
              <IconInbox width={22} height={22} />
              <b>{t('iam.orgs.nodeNotSelected')}</b>
              <span>{t('iam.orgs.selectHint')}</span>
            </div>
          ) : (
            <>
              <dl className="kv">
                <dt>{t('iam.orgs.dt.code')}</dt>
                <dd className="mono">{selectedNode.code}</dd>
                <dt>{t('iam.orgs.dt.type')}</dt>
                <dd>{orgTypeLabel(selectedNode.nodeType)}</dd>
                <dt>{t('iam.orgs.dt.source')}</dt>
                <dd>{originLabel(selectedNode.source)}</dd>
                <dt>{t('iam.orgs.dt.status')}</dt>
                <dd>
                  <span className="status">
                    <i className={'led ' + (selectedNode.status === 'ENABLED' ? 'ok' : 'err')} />
                    <b>{selectedNode.status === 'ENABLED' ? t('iam.orgs.statusEnabled') : t('iam.orgs.statusDisabled')}</b>
                  </span>
                </dd>
                <dt>{t('iam.orgs.dt.path')}</dt>
                <dd className="mono dim">{selectedNode.path}</dd>
                <dt>{t('iam.orgs.dt.updated')}</dt>
                <dd>{fmtRelative(selectedNode.updatedAt)}</dd>
              </dl>

              {managed ? (
                <>
                  <form onSubmit={onUpdate} style={{ marginTop: 14 }}>
                    <div className="row">
                      <div className="field">
                        <label className="field-label" htmlFor="on">
                          {t('iam.orgs.f.name')}
                        </label>
                        <input id="on" value={editName} onChange={(e) => setEditName(e.target.value)} />
                      </div>
                      <div className="field">
                        <label className="field-label" htmlFor="os">
                          {t('iam.orgs.f.sort')}
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
                          {t('iam.orgs.f.status')}
                        </label>
                        <select
                          id="ost"
                          value={editStatus}
                          onChange={(e) => setEditStatus(e.target.value as 'ENABLED' | 'DISABLED')}
                        >
                          <option value="ENABLED">{t('iam.orgs.optEnabled')}</option>
                          <option value="DISABLED">{t('iam.orgs.optDisabled')}</option>
                        </select>
                      </div>
                      <Perms code={ORG_UPDATE}>
                        <button type="submit" className="btn-sm" disabled={busy}>
                          <IconEdit width={13} height={13} />
                          {t('iam.orgs.save')}
                        </button>
                      </Perms>
                    </div>
                  </form>

                  <div className="row" style={{ marginTop: 12 }}>
                    <div className="field">
                        <label className="field-label" htmlFor="mv">
                          {t('iam.orgs.moveTo')}
                        </label>
                        <select id="mv" value={moveTo} onChange={(e) => setMoveTo(e.target.value)}>
                          <option value="">{t('iam.orgs.noParent')}</option>
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
                          {t('iam.orgs.move')}
                        </button>
                    </Perms>
                    <Perms code={ORG_DELETE}>
                      <button type="button" className="btn-danger btn-sm" onClick={onDelete}>
                          <IconTrash width={13} height={13} />
                          {t('iam.orgs.delete')}
                        </button>
                    </Perms>
                  </div>
                </>
              ) : (
                <p className="hint" style={{ marginTop: 12 }}>
                  {t('iam.orgs.adReadOnly')}
                </p>
              )}

              <div className="sub-block">
                <div className="sub-head">
                  <b>{t('iam.orgs.membersTitle')}</b>
                  <span className="tag">{t('iam.orgs.membersCount', { n: members.length })}</span>
                </div>
                <div className="row">
                  <div className="field">
                    <input
                      className="mono"
                      list="all-users"
                      placeholder={t('iam.orgs.memberPH')}
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
                      {t('iam.orgs.addMember')}
                    </button>
                  </Perms>
                </div>
                <div className="tag-list" style={{ marginTop: 10 }}>
                  {members.map((m) => (
                    <span key={m} className="role-chip">
                      <span className="mono">{m}</span>
                      <Perms code={ORG_MEMBER}>
                        <button type="button" className="chip-x" onClick={() => onRemoveMember(m)} aria-label={t('iam.orgs.removeMember')}>
                          ×
                        </button>
                      </Perms>
                    </span>
                  ))}
                  {!members.length && <span className="dim">{t('iam.orgs.noMembers')}</span>}
                </div>
                <p className="hint">{t('iam.orgs.memberHint')}</p>
              </div>
            </>
          )}
        </Panel>
      </div>

      {showCreate && (
        <Panel title={t('iam.orgs.newTitle')} sub={t('iam.orgs.newSub')}>
          <form onSubmit={onCreate}>
            <div className="row">
              <div className="field">
                <label className="field-label" htmlFor="pp">
                  {t('iam.orgs.f.parent')}
                </label>
                <select id="pp" value={parentId} onChange={(e) => setParentId(e.target.value)}>
                  <option value="">{t('iam.orgs.noParent')}</option>
                  {flatOptions.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label className="field-label" htmlFor="cc">
                  {t('iam.orgs.f.code')}
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
                  {t('iam.orgs.f.name')}
                </label>
                <input id="nn" placeholder={t('iam.orgs.namePH')} value={name} onChange={(e) => setName(e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label" htmlFor="nt">
                  {t('iam.orgs.f.type')}
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
                  {t('iam.orgs.f.sort')}
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
                    {busy ? t('iam.orgs.creating') : t('iam.orgs.create')}
                  </button>
                </Perms>
            </div>
          </form>
          <p className="hint">{t('iam.orgs.hint')}</p>
        </Panel>
      )}
    </div>
  );
}
