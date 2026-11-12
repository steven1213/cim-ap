import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimInput,
  CimSelect,
  CimTag,
  CimTree,
  EmptyState,
  Page,
  Panel,
  useConfirm,
  useToast,
  type CimTreeNode,
  CimButton,
  InlineAlert,
  CimField,
  CimSplitPane,
} from '@cim/ui';
import * as api from '@/lib/api';
import type { AdminUserRow, OrgNode, OrgNodeType } from '@/types';
import { fmtRelative, orgTypeLabel, originLabel } from '@/lib/format';
import { ORG_CREATE, ORG_DELETE, ORG_MEMBER, ORG_MOVE, ORG_UPDATE } from '@/lib/permCodes';
import { IconEdit, IconPlus, IconTrash } from '@/components/Icons';

/**
 * 组织架构（制造组织树）。
 *
 * <p>厂区 → 车间 → 产线 → 工序 → 班组由 IAM 自建；AD 同步来的行政部门为只读。
 * 组织是「按组织批量授权」与数据权限的上游——先把树建对，再挂人与授权。
 * 各写操作按 `iam:org:*` 权限显隐。</p>
 */

interface Bundle {
  orgs: OrgNode[];
  users: AdminUserRow[];
}

/** 建树用的中间结构（OrgNode 本身是扁平的）。 */
interface OrgTreeNode extends OrgNode {
  children: OrgTreeNode[];
}

const CREATE_TYPES: OrgNodeType[] = ['AREA', 'WORKSHOP', 'LINE', 'PROCESS', 'TEAM'];

export default function OrgPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

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
      api.get<OrgNode[]>('/admin/orgs', undefined, { signal }),
      api.get<AdminUserRow[]>('/admin/users', undefined, { signal }),
    ]);
    return { orgs, users };
  });

  const nodes = bundleRes.data?.orgs ?? [];
  const users = bundleRes.data?.users ?? [];

  const membersRes = useResource<string[]>(
    ({ signal }) =>
      api.get<string[]>(`/admin/orgs/${encodeURIComponent(selected ?? '')}/users`, undefined, { signal }),
    { deps: [selected], enabled: !!selected },
  );
  const members = selected ? membersRes.data ?? [] : [];

  /** 首屏展开全部：只在第一批数据到达时做一次，之后用户的折叠状态不再被覆盖。 */
  const seeded = useRef(false);
  useEffect(() => {
    if (seeded.current || !nodes.length) return;
    seeded.current = true;
    setExpanded(nodes.map((n) => n.id));
  }, [nodes]);

  const selectedNode = nodes.find((n) => n.id === selected) ?? null;
  const managed = selectedNode?.source === 'IAM_MANAGED';

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
              {t('iam.orgs.statusDisabled')}
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
      await api.post('/admin/orgs', {
        parentId: parentId || null,
        code: code.trim(),
        name: name.trim(),
        nodeType,
        sortNo: Number(sortNo) || 0,
      });
      toast.success(t('iam.orgs.msgCreated', { name: name.trim() }));
      setCode('');
      setName('');
      setShowCreate(false);
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.orgs.errCreate'));
    } finally {
      setBusy(false);
    }
  }

  async function onUpdate() {
    if (!selected) return;
    setBusy(true);
    try {
      await api.put(`/admin/orgs/${encodeURIComponent(selected)}`, {
        name: editName.trim(),
        sortNo: Number(editSort) || 0,
        status: editStatus,
      });
      toast.success(t('iam.orgs.msgSaved'));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.orgs.errSave'));
    } finally {
      setBusy(false);
    }
  }

  async function onMove() {
    if (!selected) return;
    try {
      await api.put(`/admin/orgs/${encodeURIComponent(selected)}/move`, { newParentId: moveTo || null });
      toast.success(t('iam.orgs.msgMoved'));
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.orgs.errMove'));
    }
  }

  async function onDelete() {
    if (!selectedNode) return;
    const ok = await confirm({
      title: t('iam.orgs.delete'),
      content: t('iam.orgs.confirmDelete', { name: selectedNode.name }),
      danger: true,
      okKey: 'iam.common.delete',
      cancelKey: 'iam.common.cancel',
    });
    if (!ok) return;
    try {
      await api.del(`/admin/orgs/${encodeURIComponent(selectedNode.id)}`);
      toast.success(t('iam.orgs.msgDeleted'));
      setSelected(null);
      bundleRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.orgs.errDelete'));
    }
  }

  async function onAddMember() {
    if (!selected || !addUser.trim()) return;
    const uid = addUser.trim();
    try {
      const cur = await api.get<{ orgId: string; primary: boolean }[]>(
        `/admin/users/${encodeURIComponent(uid)}/orgs`,
      );
      if (cur.some((o) => o.orgId === selected)) {
        toast.info(t('iam.orgs.msgMemberAdded', { uid }));
        return;
      }
      const next = [
        ...cur.map((o) => ({ orgId: o.orgId, primary: o.primary })),
        { orgId: selected, primary: cur.length === 0 },
      ];
      await api.put(`/admin/users/${encodeURIComponent(uid)}/orgs`, { orgs: next });
      toast.success(t('iam.orgs.msgMemberMoved', { uid }));
      setAddUser('');
      membersRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.orgs.errMember'));
    }
  }

  async function onRemoveMember(uid: string) {
    if (!selected) return;
    try {
      const cur = await api.get<{ orgId: string; primary: boolean }[]>(
        `/admin/users/${encodeURIComponent(uid)}/orgs`,
      );
      const next = cur.filter((o) => o.orgId !== selected).map((o) => ({ orgId: o.orgId, primary: o.primary }));
      await api.put(`/admin/users/${encodeURIComponent(uid)}/orgs`, { orgs: next });
      toast.success(t('iam.orgs.msgMemberRemoved', { uid }));
      membersRes.refresh();
    } catch (e: any) {
      toast.error(e?.msg || t('iam.orgs.errRemove'));
    }
  }

  return (
    <Page>
      <CimSplitPane>
        <Panel
          className="scroll-y"
          title={t('iam.orgs.title')}
          actions={
            <>
              <CimTag>{t('iam.orgs.count', { n: nodes.length })}</CimTag>
              <Can perm={ORG_CREATE}>
                <CimButton type="primary" size="small" onClick={() => setShowCreate((v) => !v)}>
                  <IconPlus width={13} height={13} />
                  {t('iam.orgs.newNode')}
                </CimButton>
              </Can>
            </>
          }
        >
          {bundleRes.error ? (
            <div style={{ padding: 14 }}>
              <InlineAlert tone="err">{bundleRes.error.message || t('iam.orgs.errLoad')}</InlineAlert>
              <CimButton type="primary" size="small" style={{ marginTop: 10 }} onClick={bundleRes.refresh}>
                {t('iam.common.refresh')}
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
              <EmptyState titleKey="iam.orgs.emptyTitle" hintKey="iam.orgs.emptyHint" />
            </div>
          )}
        </Panel>

        <Panel
          title={selectedNode ? t('iam.orgs.nodeTitle', { name: selectedNode.name }) : t('iam.orgs.nodeDetail')}
          sub={
            selectedNode
              ? t('iam.orgs.nodeSub', {
                  src: originLabel(selectedNode.source),
                  ro: managed ? t('iam.orgs.nodeReadOnly') : '',
                })
              : t('iam.orgs.selectNode')
          }
        >
          {!selectedNode ? (
            <EmptyState titleKey="iam.orgs.nodeNotSelected" hintKey="iam.orgs.selectHint" />
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
                  <CimTag size="small" tone={selectedNode.status === 'ENABLED' ? 'ok' : 'err'}>
                    {selectedNode.status === 'ENABLED'
                      ? t('iam.orgs.statusEnabled')
                      : t('iam.orgs.statusDisabled')}
                  </CimTag>
                </dd>
                <dt>{t('iam.orgs.dt.path')}</dt>
                <dd className="mono dim">{selectedNode.path}</dd>
                <dt>{t('iam.orgs.dt.updated')}</dt>
                <dd>{fmtRelative(selectedNode.updatedAt)}</dd>
              </dl>

              {managed ? (
                <>
                  <div className="row" style={{ marginTop: 14 }}>
                    <CimField label={t('iam.orgs.f.name')} htmlFor="on">
                      <CimInput id="on" value={editName} onChange={setEditName} width={180} />
                    </CimField>
                    <CimField label={t('iam.orgs.f.sort')} htmlFor="os">
                      <CimInput
                        id="os"
                        mono
                        value={String(editSort)}
                        onChange={(v) => setEditSort(Number(v) || 0)}
                        width={100}
                      />
                    </CimField>
                    <CimField label={t('iam.orgs.f.status')} htmlFor="ost">
                      <CimSelect
                        id="ost"
                        value={editStatus}
                        onChange={(v) =>
                          setEditStatus((typeof v === 'string' ? v : 'ENABLED') as 'ENABLED' | 'DISABLED')
                        }
                        options={[
                          { value: 'ENABLED', label: t('iam.orgs.optEnabled') },
                          { value: 'DISABLED', label: t('iam.orgs.optDisabled') },
                        ]}
                        allowClear={false}
                        width={140}
                      />
                    </CimField>
                    <Can perm={ORG_UPDATE}>
                      <CimButton type="primary" size="small" htmlType="button" disabled={busy} onClick={() => void onUpdate()}>
                        <IconEdit width={13} height={13} />
                        {t('iam.orgs.save')}
                      </CimButton>
                    </Can>
                  </div>

                  <div className="row" style={{ marginTop: 12 }}>
                    <CimField label={t('iam.orgs.moveTo')} htmlFor="mv">
                      <CimSelect
                        id="mv"
                        value={moveTo}
                        onChange={(v) => setMoveTo(typeof v === 'string' ? v : '')}
                        options={[
                          { value: '', label: t('iam.orgs.noParent') },
                          ...flatOptions
                            .filter((o) => o.id !== selectedNode.id)
                            .map((o) => ({ value: o.id, label: o.label })),
                        ]}
                        allowClear={false}
                        width={300}
                      />
                    </CimField>
                    <Can perm={ORG_MOVE}>
                      <CimButton size="small" htmlType="button" onClick={() => void onMove()}>
                        {t('iam.orgs.move')}
                      </CimButton>
                    </Can>
                    <Can perm={ORG_DELETE}>
                      <CimButton danger size="small" htmlType="button" onClick={() => void onDelete()}>
                        <IconTrash width={13} height={13} />
                        {t('iam.orgs.delete')}
                      </CimButton>
                    </Can>
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
                  <CimTag>{t('iam.orgs.membersCount', { n: members.length })}</CimTag>
                </div>
                <div className="row">
                  <CimField>
                    <CimSelect
                      value={addUser}
                      onChange={(v) => setAddUser(typeof v === 'string' ? v : '')}
                      // 原实现用 datalist：Safari 不支持、且只能按 user id 前缀匹配；换成可选可搜的下拉
                      options={users.map((u) => ({ value: u.userId, label: u.displayName || u.username, hint: u.username }))}
                      placeholderKey="iam.orgs.memberPH"
                      width={260}
                    />
                  </CimField>
                  <Can perm={ORG_MEMBER}>
                    <CimButton
                      type="primary"
                      size="small"
                      htmlType="button"
                      onClick={() => void onAddMember()}
                      disabled={!addUser.trim()}
                    >
                      <IconPlus width={13} height={13} />
                      {t('iam.orgs.addMember')}
                    </CimButton>
                  </Can>
                </div>
                <div className="tag-list" style={{ marginTop: 10 }}>
                  {members.map((m) => (
                    <span key={m} className="role-chip">
                      <span className="mono">{m}</span>
                      <Can perm={ORG_MEMBER}>
                        <CimButton
                          bare
                          className="chip-x"
                          htmlType="button"
                          onClick={() => void onRemoveMember(m)}
                          aria-label={t('iam.orgs.removeMember')}
                        >
                          ×
                        </CimButton>
                      </Can>
                    </span>
                  ))}
                  {!members.length && <span className="dim">{t('iam.orgs.noMembers')}</span>}
                </div>
                <p className="hint">{t('iam.orgs.memberHint')}</p>
              </div>
            </>
          )}
        </Panel>
      </CimSplitPane>

      {showCreate && (
        <Panel title={t('iam.orgs.newTitle')} sub={t('iam.orgs.newSub')}>
          <div className="row">
            <CimField label={t('iam.orgs.f.parent')} htmlFor="pp">
              <CimSelect
                id="pp"
                value={parentId}
                onChange={(v) => setParentId(typeof v === 'string' ? v : '')}
                options={[
                  { value: '', label: t('iam.orgs.noParent') },
                  ...flatOptions.map((o) => ({ value: o.id, label: o.label })),
                ]}
                allowClear={false}
                width={260}
              />
            </CimField>
            <CimField label={t('iam.orgs.f.code')} htmlFor="cc">
              <CimInput
                id="cc"
                mono
                placeholder="LINE-ETCH-01"
                value={code}
                onChange={setCode}
                width={170}
              />
            </CimField>
            <CimField label={t('iam.orgs.f.name')} htmlFor="nn">
              <CimInput id="nn" placeholder={t('iam.orgs.namePH')} value={name} onChange={setName} width={160} />
            </CimField>
            <CimField label={t('iam.orgs.f.type')} htmlFor="nt">
              <CimSelect
                id="nt"
                value={nodeType}
                onChange={(v) => setNodeType((typeof v === 'string' ? v : 'WORKSHOP') as OrgNodeType)}
                options={CREATE_TYPES.map((x) => ({ value: x, label: orgTypeLabel(x) }))}
                allowClear={false}
                width={130}
              />
            </CimField>
            <CimField label={t('iam.orgs.f.sort')} htmlFor="sn">
              <CimInput
                id="sn"
                mono
                value={String(sortNo)}
                onChange={(v) => setSortNo(Number(v) || 0)}
                width={90}
              />
            </CimField>
            <Can perm={ORG_CREATE}>
              <CimButton
                type="primary"
                htmlType="button"
                disabled={busy || !code.trim() || !name.trim()}
                onClick={() => void onCreate()}
              >
                {busy ? t('iam.orgs.creating') : t('iam.orgs.create')}
              </CimButton>
            </Can>
          </div>
          <p className="hint">{t('iam.orgs.hint')}</p>
        </Panel>
      )}
    </Page>
  );
}
