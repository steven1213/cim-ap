// 组织架构（共享，六页系统配置之一）。
//
// 主数据在 IAM（org_node 自引用树）。IAM 宿主传 readOnly=false + 缺省端点（/admin/orgs、
// /admin/users）即得全 CRUD；业务系统宿主传 readOnly=true + 目录代理前缀
// （如 /mds/admin/directory/orgs），页面隐藏全部增删改控件，仅保留树浏览、
// 节点详情与成员名册展示——与 IAM 页面同一套组件与视觉，样式天然统一。
//
// 权限：动作 UI 以 useAdminHasAll 判权显隐（不使用 @cim/ui 的 Can——那依赖宿主
// 权限上下文组件；本包自持 AdminProvider 注入的 hasAll，宿主耦合更小）。
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  CimButton,
  CimInput,
  CimSelect,
  CimTag,
  CimTree,
  EmptyState,
  IconEdit,
  IconPlus,
  IconTrash,
  InlineAlert,
  Page,
  Panel,
  CimField,
  CimSplitPane,
  useConfirm,
  useToast,
  type CimTreeNode,
} from '@cim/ui';
import { useAdminHasAll, useAdminHttp } from '../context';
import { IAM_CREATE_PERMS, ORG_PERMS, type AdminPermOverrides } from '../perms';
import type { AdminUserRow, OrgNode, OrgNodeType } from '../types';
import { fmtRelative, orgTypeLabel, originLabel } from '../format';

export interface OrgPageProps {
  /** 只读模式（业务系统）：隐藏全部增删改控件，数据走目录代理。 */
  readOnly?: boolean;
  perms?: AdminPermOverrides;
  /** 组织端点前缀（缺省 /admin/orgs；只读宿主传如 /mds/admin/directory/orgs）。 */
  orgPath?: string;
  /** 人员端点前缀（缺省 /admin/users；只读宿主传如 /mds/admin/directory/users）。 */
  usersPath?: string;
}

interface Bundle {
  orgs: OrgNode[];
  users: AdminUserRow[];
}

/** 建树用的中间结构（OrgNode 本身是扁平的）。 */
interface OrgTreeNode extends OrgNode {
  children: OrgTreeNode[];
}

const CREATE_TYPES: OrgNodeType[] = ['AREA', 'WORKSHOP', 'LINE', 'PROCESS', 'TEAM'];

/** 组织页（六页共享）。 */
export default function OrgPage({ readOnly = false, perms, orgPath = '/admin/orgs', usersPath = '/admin/users' }: OrgPageProps) {
  const { t } = useTranslation();
  const http = useAdminHttp();
  const hasAll = useAdminHasAll();
  const toast = useToast();
  const confirm = useConfirm();
  const p = { ...ORG_PERMS, ...IAM_CREATE_PERMS, ...perms };
  const canCreate = !readOnly && hasAll(p.orgCreate ?? ['iam:org:create']);
  const canUpdate = !readOnly && hasAll(p.save ?? []);
  const canMove = !readOnly && hasAll(p.move ?? []);
  const canRemove = !readOnly && hasAll(p.remove ?? []);
  const canMember = !readOnly && hasAll(p.member ?? []);

  const [expanded, setExpanded] = useState<string[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

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

  const bundleRes = useResource<Bundle>(async ({ signal }) => {
    const [orgs, users] = await Promise.all([
      http.get<OrgNode[]>(orgPath, undefined, { signal }),
      http.get<AdminUserRow[]>(usersPath, undefined, { signal }),
    ]);
    return { orgs: orgs ?? [], users: users ?? [] };
  });

  const nodes = bundleRes.data?.orgs ?? [];
  const users = bundleRes.data?.users ?? [];

  const membersRes = useResource<string[]>(
    async ({ signal }) =>
      (await http.get<string[]>(`${orgPath}/${encodeURIComponent(selected ?? '')}/users`, undefined, { signal })) ?? [],
    { deps: [selected], enabled: !!selected },
  );
  const members = selected ? membersRes.data ?? [] : [];

  /** 成员 ID → 展示名（只读模式下名册更可读；IAM 全量用户表里也直接映射）。 */
  const memberName = useMemo(() => {
    const m = new Map<string, string>();
    users.forEach((u) => m.set(u.userId, u.displayName || u.username));
    return m;
  }, [users]);

  /** 首屏展开全部：只在第一批数据到达时做一次，之后用户的折叠状态不再被覆盖。 */
  const seeded = useRef(false);
  useEffect(() => {
    if (seeded.current || !nodes.length) return;
    seeded.current = true;
    setExpanded(nodes.map((n) => n.id));
  }, [nodes]);

  const selectedNode = nodes.find((n) => n.id === selected) ?? null;
  const managed = !readOnly && selectedNode?.source === 'IAM_MANAGED';

  /** 选中节点变化 → 把它的当前值灌进右侧表单（用户在这之后改的是草稿，不回写树）。 */
  useEffect(() => {
    if (!selectedNode) return;
    setEditName(selectedNode.name);
    setEditSort(selectedNode.sortNo);
    setEditStatus(selectedNode.status);
    setMoveTo(selectedNode.parentId ?? '');
  }, [selectedNode]);

  const treeNodes = useMemo<CimTreeNode[]>(() => {
    const byId = new Map<string, OrgTreeNode>();
    nodes.forEach((n) => byId.set(n.id, { ...n, children: [] }));
    const roots: OrgTreeNode[] = [];
    byId.forEach((n) => {
      const parent = n.parentId ? byId.get(n.parentId) : undefined;
      if (parent) parent.children.push(n);
      else roots.push(n);
    });
    const sort = (list: OrgTreeNode[]) => {
      list.sort((a, b) => a.sortNo - b.sortNo || a.code.localeCompare(b.code));
      list.forEach((n) => sort(n.children));
    };
    sort(roots);

    const toNode = (n: OrgTreeNode): CimTreeNode => ({
      key: n.id,
      searchText: `${n.name} ${n.code}`,
      labelNode: (
        <span className="tree-node-inline">
          <span className="tree-name">{n.name}</span>
          <span className="cim-mono dim">{n.code}</span>
          <CimTag size="small">{orgTypeLabel(n.nodeType)}</CimTag>
          {n.source === 'AD_SYNCED' && (
            <CimTag size="small" tone="run">
              AD
            </CimTag>
          )}
          {n.status === 'DISABLED' && (
            <CimTag size="small" tone="err">
              {t('admin.orgs.statusDisabled')}
            </CimTag>
          )}
        </span>
      ),
      children: n.children.map(toNode),
    });
    return roots.map(toNode);
  }, [nodes, t]);

  const flatOptions = useMemo(
    () =>
      nodes
        .slice()
        .sort((a, b) => a.path.localeCompare(b.path))
        .map((n) => ({
          id: n.id,
          label: `${'·'.repeat(Math.max(0, n.path.split('/').filter(Boolean).length - 1))} ${n.name} (${n.code})`,
        })),
    [nodes],
  );

  async function onCreate() {
    setBusy(true);
    try {
      await http.post(orgPath, {
        parentId: parentId || null,
        code: code.trim(),
        name: name.trim(),
        nodeType,
        sortNo: Number(sortNo) || 0,
      });
      toast.success(t('admin.orgs.msgCreated', { name: name.trim() }));
      setCode('');
      setName('');
      setShowCreate(false);
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.orgs.errCreate'));
    } finally {
      setBusy(false);
    }
  }

  async function onUpdate() {
    if (!selected) return;
    setBusy(true);
    try {
      await http.put(`${orgPath}/${encodeURIComponent(selected)}`, {
        name: editName.trim(),
        sortNo: Number(editSort) || 0,
        status: editStatus,
      });
      toast.success(t('admin.orgs.msgSaved'));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.orgs.errSave'));
    } finally {
      setBusy(false);
    }
  }

  async function onMove() {
    if (!selected) return;
    try {
      await http.put(`${orgPath}/${encodeURIComponent(selected)}/move`, { newParentId: moveTo || null });
      toast.success(t('admin.orgs.msgMoved'));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.orgs.errMove'));
    }
  }

  async function onDelete() {
    if (!selectedNode) return;
    const ok = await confirm({
      title: t('admin.orgs.delete'),
      content: t('admin.orgs.confirmDelete', { name: selectedNode.name }),
      danger: true,
      okKey: 'admin.common.delete',
      cancelKey: 'admin.common.cancel',
    });
    if (!ok) return;
    try {
      await http.del(`${orgPath}/${encodeURIComponent(selectedNode.id)}`);
      toast.success(t('admin.orgs.msgDeleted'));
      setSelected(null);
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.orgs.errDelete'));
    }
  }

  async function onAddMember() {
    if (!selected || !addUser.trim()) return;
    const uid = addUser.trim();
    try {
      const cur = (await http.get<{ orgId: string; primary: boolean }[]>(
        `${usersPath}/${encodeURIComponent(uid)}/orgs`,
      )) ?? [];
      if (cur.some((o) => o.orgId === selected)) {
        toast.info(t('admin.orgs.msgMemberAdded', { uid }));
        return;
      }
      const next = [
        ...cur.map((o) => ({ orgId: o.orgId, primary: o.primary })),
        { orgId: selected, primary: cur.length === 0 },
      ];
      await http.put(`${usersPath}/${encodeURIComponent(uid)}/orgs`, { orgs: next });
      toast.success(t('admin.orgs.msgMemberMoved', { uid }));
      setAddUser('');
      membersRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.orgs.errMember'));
    }
  }

  async function onRemoveMember(uid: string) {
    if (!selected) return;
    try {
      const cur = (await http.get<{ orgId: string; primary: boolean }[]>(
        `${usersPath}/${encodeURIComponent(uid)}/orgs`,
      )) ?? [];
      const next = cur.filter((o) => o.orgId !== selected).map((o) => ({ orgId: o.orgId, primary: o.primary }));
      await http.put(`${usersPath}/${encodeURIComponent(uid)}/orgs`, { orgs: next });
      toast.success(t('admin.orgs.msgMemberRemoved', { uid }));
      membersRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('admin.orgs.errRemove'));
    }
  }

  return (
    <Page>
      {readOnly && (
        <div style={{ marginBottom: 10 }}>
          <InlineAlert tone="info">{t('admin.orgs.readOnlyHint')}</InlineAlert>
        </div>
      )}
      <CimSplitPane>
        <Panel
          className="scroll-y"
          title={t('admin.orgs.title')}
          actions={
            <>
              <CimTag>{t('admin.orgs.count', { n: nodes.length })}</CimTag>
              {canCreate && (
                <CimButton type="primary" size="small" onClick={() => setShowCreate((v) => !v)}>
                  <IconPlus width={13} height={13} />
                  {t('admin.orgs.newNode')}
                </CimButton>
              )}
            </>
          }
        >
          {bundleRes.error ? (
            <div style={{ padding: 14 }}>
              <InlineAlert tone="err">{bundleRes.error.message || t('admin.orgs.errLoad')}</InlineAlert>
              <CimButton type="primary" size="small" style={{ marginTop: 10 }} onClick={bundleRes.refresh}>
                {t('admin.common.refresh')}
              </CimButton>
            </div>
          ) : treeNodes.length ? (
            <div className="tree-pad">
              <CimTree
                nodes={treeNodes}
                selectedKeys={selected ? [selected] : []}
                onSelect={(k) => setSelected(k)}
                expandedKeys={expanded}
                onExpand={setExpanded}
                showLine
              />
            </div>
          ) : (
            <div style={{ padding: 14 }}>
              <EmptyState titleKey="admin.orgs.emptyTitle" hintKey="admin.orgs.emptyHint" />
            </div>
          )}
        </Panel>

        <Panel
          title={selectedNode ? t('admin.orgs.nodeTitle', { name: selectedNode.name }) : t('admin.orgs.nodeDetail')}
          sub={
            selectedNode
              ? t('admin.orgs.nodeSub', {
                  src: originLabel(selectedNode.source),
                  ro: managed ? t('admin.orgs.nodeReadOnly') : '',
                })
              : t('admin.orgs.selectNode')
          }
        >
          {!selectedNode ? (
            <EmptyState titleKey="admin.orgs.nodeNotSelected" hintKey="admin.orgs.selectHint" />
          ) : (
            <>
              <dl className="kv">
                <dt>{t('admin.orgs.dt.code')}</dt>
                <dd className="mono">{selectedNode.code}</dd>
                <dt>{t('admin.orgs.dt.type')}</dt>
                <dd>{orgTypeLabel(selectedNode.nodeType)}</dd>
                <dt>{t('admin.orgs.dt.source')}</dt>
                <dd>{originLabel(selectedNode.source)}</dd>
                <dt>{t('admin.orgs.dt.status')}</dt>
                <dd>
                  <CimTag size="small" tone={selectedNode.status === 'ENABLED' ? 'ok' : 'err'}>
                    {selectedNode.status === 'ENABLED'
                      ? t('admin.orgs.statusEnabled')
                      : t('admin.orgs.statusDisabled')}
                  </CimTag>
                </dd>
                <dt>{t('admin.orgs.dt.path')}</dt>
                <dd className="mono dim">{selectedNode.path}</dd>
                <dt>{t('admin.orgs.dt.updated')}</dt>
                <dd>{fmtRelative(selectedNode.updatedAt)}</dd>
              </dl>

              {managed && (
                <>
                  <div className="row" style={{ marginTop: 14 }}>
                    <CimField label={t('admin.orgs.f.name')} htmlFor="on">
                      <CimInput id="on" value={editName} onChange={setEditName} width={180} />
                    </CimField>
                    <CimField label={t('admin.orgs.f.sort')} htmlFor="os">
                      <CimInput
                        id="os"
                        mono
                        value={String(editSort)}
                        onChange={(v) => setEditSort(Number(v) || 0)}
                        width={100}
                      />
                    </CimField>
                    <CimField label={t('admin.orgs.f.status')} htmlFor="ost">
                      <CimSelect
                        id="ost"
                        value={editStatus}
                        onChange={(v) =>
                          setEditStatus((typeof v === 'string' ? v : 'ENABLED') as 'ENABLED' | 'DISABLED')
                        }
                        options={[
                          { value: 'ENABLED', label: t('admin.orgs.optEnabled') },
                          { value: 'DISABLED', label: t('admin.orgs.optDisabled') },
                        ]}
                        allowClear={false}
                        width={140}
                      />
                    </CimField>
                    {canUpdate && (
                      <CimButton type="primary" size="small" htmlType="button" disabled={busy} onClick={() => void onUpdate()}>
                        <IconEdit width={13} height={13} />
                        {t('admin.orgs.save')}
                      </CimButton>
                    )}
                  </div>

                  {(canMove || canRemove) && (
                    <div className="row" style={{ marginTop: 12 }}>
                      {canMove && (
                        <>
                          <CimField label={t('admin.orgs.moveTo')} htmlFor="mv">
                            <CimSelect
                              id="mv"
                              value={moveTo}
                              onChange={(v) => setMoveTo(typeof v === 'string' ? v : '')}
                              options={[
                                { value: '', label: t('admin.orgs.noParent') },
                                ...flatOptions
                                  .filter((o) => o.id !== selectedNode.id)
                                  .map((o) => ({ value: o.id, label: o.label })),
                              ]}
                              allowClear={false}
                              width={300}
                            />
                          </CimField>
                          <CimButton size="small" htmlType="button" onClick={() => void onMove()}>
                            {t('admin.orgs.move')}
                          </CimButton>
                        </>
                      )}
                      {canRemove && (
                        <CimButton danger size="small" htmlType="button" onClick={() => void onDelete()}>
                          <IconTrash width={13} height={13} />
                          {t('admin.orgs.delete')}
                        </CimButton>
                      )}
                    </div>
                  )}
                </>
              )}
              {!readOnly && !managed && (
                <p className="hint" style={{ marginTop: 12 }}>
                  {t('admin.orgs.adReadOnly')}
                </p>
              )}

              <div className="sub-block">
                <div className="sub-head">
                  <b>{t('admin.orgs.membersTitle')}</b>
                  <CimTag>{t('admin.orgs.membersCount', { n: members.length })}</CimTag>
                </div>
                {canMember && (
                  <div className="row">
                    <CimField>
                      <CimSelect
                        value={addUser}
                        onChange={(v) => setAddUser(typeof v === 'string' ? v : '')}
                        options={users.map((u) => ({ value: u.userId, label: u.displayName || u.username, hint: u.username }))}
                        placeholderKey="admin.orgs.memberPH"
                        width={260}
                      />
                    </CimField>
                    <CimButton
                      type="primary"
                      size="small"
                      htmlType="button"
                      onClick={() => void onAddMember()}
                      disabled={!addUser.trim()}
                    >
                      <IconPlus width={13} height={13} />
                      {t('admin.orgs.addMember')}
                    </CimButton>
                  </div>
                )}
                <div className="tag-list" style={{ marginTop: 10 }}>
                  {members.map((m) => (
                    <span key={m} className="role-chip">
                      <span className="mono" title={m}>{memberName.get(m) ?? m}</span>
                      {canMember && (
                        <CimButton
                          bare
                          className="chip-x"
                          htmlType="button"
                          onClick={() => void onRemoveMember(m)}
                          aria-label={t('admin.orgs.removeMember')}
                        >
                          ×
                        </CimButton>
                      )}
                    </span>
                  ))}
                  {!members.length && <span className="dim">{t('admin.orgs.noMembers')}</span>}
                </div>
                {!readOnly && <p className="hint">{t('admin.orgs.memberHint')}</p>}
              </div>
            </>
          )}
        </Panel>
      </CimSplitPane>

      {showCreate && canCreate && (
        <Panel title={t('admin.orgs.newTitle')} sub={t('admin.orgs.newSub')}>
          <div className="row">
            <CimField label={t('admin.orgs.f.parent')} htmlFor="pp">
              <CimSelect
                id="pp"
                value={parentId}
                onChange={(v) => setParentId(typeof v === 'string' ? v : '')}
                options={[
                  { value: '', label: t('admin.orgs.noParent') },
                  ...flatOptions.map((o) => ({ value: o.id, label: o.label })),
                ]}
                allowClear={false}
                width={260}
              />
            </CimField>
            <CimField label={t('admin.orgs.f.code')} htmlFor="cc">
              <CimInput
                id="cc"
                mono
                placeholder="LINE-ETCH-01"
                value={code}
                onChange={setCode}
                width={170}
              />
            </CimField>
            <CimField label={t('admin.orgs.f.name')} htmlFor="nn">
              <CimInput id="nn" placeholder={t('admin.orgs.namePH')} value={name} onChange={setName} width={160} />
            </CimField>
            <CimField label={t('admin.orgs.f.type')} htmlFor="nt">
              <CimSelect
                id="nt"
                value={nodeType}
                onChange={(v) => setNodeType((typeof v === 'string' ? v : 'WORKSHOP') as OrgNodeType)}
                options={CREATE_TYPES.map((x) => ({ value: x, label: orgTypeLabel(x) }))}
                allowClear={false}
                width={130}
              />
            </CimField>
            <CimField label={t('admin.orgs.f.sort')} htmlFor="sn">
              <CimInput
                id="sn"
                mono
                value={String(sortNo)}
                onChange={(v) => setSortNo(Number(v) || 0)}
                width={90}
              />
            </CimField>
            <CimButton
              type="primary"
              htmlType="button"
              disabled={busy || !code.trim() || !name.trim()}
              onClick={() => void onCreate()}
            >
              {busy ? t('admin.orgs.creating') : t('admin.orgs.create')}
            </CimButton>
          </div>
          <p className="hint">{t('admin.orgs.hint')}</p>
        </Panel>
      )}
    </Page>
  );
}
