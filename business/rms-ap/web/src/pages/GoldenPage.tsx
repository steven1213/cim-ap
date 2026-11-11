import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimModal,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  PageHead,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import { GOLDEN_MANAGE } from '@/lib/permCodes';
import type { GoldenOverride } from '@/types';

/**
 * Golden 机差覆盖层页（FR-G1~G3，Req 16/30/31）。
 *
 * 待审的行才有「批准/驳回」，其余只留删除——这个判断写在列渲染里，
 * 不再散落在 `<td>` 的手写分支中。
 */
export default function GoldenPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();
  const [show, setShow] = useState(false);

  const { data, loading, error, refresh } = useResource(() => api.listGoldenOverrides());
  const rows = data ?? [];

  const decide = async (x: GoldenOverride, approve: boolean) => {
    try {
      if (approve) await api.approveGoldenOverride(x.id, 'ok');
      else await api.rejectGoldenOverride(x.id);
      toast.success(t(approve ? 'rms.common.approved' : 'rms.common.rejected'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const doDelete = async (x: GoldenOverride) => {
    const ok = await confirm({
      title: t('rms.common.delete'),
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteGoldenOverride(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const statusTone = (s: GoldenOverride['status']) =>
    s === 'APPROVED' ? 'ok' : s === 'REJECTED' ? 'err' : 'warn';

  const columns = useMemo<Array<DataTableColumn<GoldenOverride>>>(
    () => [
      {
        key: 'device',
        titleKey: 'rms.golden.device',
        width: 170,
        render: (r) => <span className="cim-mono">{r.deviceCode ?? r.deviceId}</span>,
      },
      {
        key: 'recipe',
        titleKey: 'rms.golden.recipe',
        width: 170,
        render: (r) => <span className="cim-mono">{r.recipeCode ?? t('rms.common.dash')}</span>,
      },
      {
        key: 'paramKey',
        titleKey: 'rms.golden.paramKey',
        width: 190,
        render: (r) => <span className="cim-mono">{r.paramKey}</span>,
      },
      {
        key: 'range',
        titleKey: 'rms.golden.allowRange',
        width: 170,
        render: (r) => (
          <span className="cim-mono">
            [{r.allowMin ?? '-'}, {r.allowMax ?? '-'}]
          </span>
        ),
      },
      {
        key: 'reason',
        titleKey: 'rms.golden.reason',
        render: (r) => <span className="muted">{r.reason ?? t('rms.common.dash')}</span>,
      },
      {
        key: 'status',
        titleKey: 'rms.golden.status',
        width: 100,
        render: (r) => (
          <CimTag tone={statusTone(r.status)}>{t(`rms.golden.status.${r.status}`)}</CimTag>
        ),
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 200,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={GOLDEN_MANAGE}>
              {r.status === 'PENDING' ? (
                <>
                  <button className="btn btn-ghost btn-sm" onClick={() => void decide(r, true)}>
                    {t('rms.common.approve')}
                  </button>
                  <button className="btn btn-ghost btn-sm danger" onClick={() => void decide(r, false)}>
                    {t('rms.common.reject')}
                  </button>
                </>
              ) : null}
              <button className="btn btn-ghost btn-sm danger" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </button>
            </Can>
          </span>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [t],
  );

  return (
    <Page>
      <PageHead
        titleKey="rms.golden.title"
        subtitleKey="rms.golden.sub"
        actions={
          <Can perm={GOLDEN_MANAGE}>
            <button className="btn btn-primary btn-sm" onClick={() => setShow(true)}>
              + {t('rms.golden.submit')}
            </button>
          </Can>
        }
      />

      <DataTable<GoldenOverride>
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="rms.golden"
        emptyTitleKey="rms.golden.empty"
      />

      {show && (
        <GoldenModal
          onClose={() => setShow(false)}
          onDone={() => {
            setShow(false);
            toast.success(t('rms.common.created'));
            refresh();
          }}
        />
      )}
    </Page>
  );
}

interface GoldenForm {
  deviceId: string;
  recipeId: string;
  paramKey: string;
  allowMin: string;
  allowMax: string;
  reason: string;
}

function GoldenModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [form, setForm] = useState<GoldenForm>({
    deviceId: '',
    recipeId: '',
    paramKey: '',
    allowMin: '',
    allowMax: '',
    reason: '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof GoldenForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const { data: devices } = useResource(() => api.listDevices());
  // 只有 golden 族配方能挂 Golden 覆盖层，与旧实现的取数口径一致
  const { data: recipes } = useResource(() => api.listRecipes({ golden: true }));

  const deviceOptions = useMemo(
    () => (devices ?? []).map((d) => ({ value: d.id, label: d.code })),
    [devices],
  );
  const recipeOptions = useMemo(
    () => (recipes ?? []).map((r) => ({ value: r.id, label: r.code })),
    [recipes],
  );

  const fields = useMemo<Array<FormField<GoldenForm>>>(
    () => [
      {
        kind: 'select',
        key: 'deviceId',
        labelKey: 'rms.golden.device',
        required: true,
        options: deviceOptions,
        placeholderKey: 'rms.common.select',
      },
      {
        kind: 'select',
        key: 'recipeId',
        labelKey: 'rms.golden.recipe',
        options: recipeOptions,
        placeholderKey: 'rms.common.select',
      },
      {
        kind: 'text',
        key: 'paramKey',
        labelKey: 'rms.golden.paramKey',
        required: true,
        mono: true,
        // 格式示例，不翻译（与旧实现的 placeholder="CHAMBER.TEMP" 一致）
        placeholder: 'CHAMBER.TEMP',
      },
      { kind: 'text', key: 'allowMin', labelKey: 'rms.golden.allowMin', mono: true },
      { kind: 'text', key: 'allowMax', labelKey: 'rms.golden.allowMax', mono: true },
      { kind: 'text', key: 'reason', labelKey: 'rms.golden.reason', span: 2 },
    ],
    [deviceOptions, recipeOptions],
  );

  async function submit() {
    const next: Partial<Record<keyof GoldenForm, string>> = {};
    if (!form.deviceId) next.deviceId = t('rms.common.required');
    if (!form.paramKey) next.paramKey = t('rms.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      await api.submitGoldenOverride({
        deviceId: form.deviceId,
        recipeId: form.recipeId || null,
        paramKey: form.paramKey,
        allowMin: form.allowMin || null,
        allowMax: form.allowMax || null,
        reason: form.reason || null,
      });
      onDone();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <CimModal
      open
      onClose={onClose}
      titleKey="rms.golden.submit"
      onOk={() => void submit()}
      okKey="rms.common.submit"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={640}
    >
      <FormGrid<GoldenForm>
        fields={fields}
        value={form}
        onChange={(patch) => setForm((prev) => ({ ...prev, ...patch }))}
        errors={fieldErrors}
        columns={2}
      />
      {err ? (
        <p className="cim-field-error" style={{ marginTop: 10 }}>
          {err}
        </p>
      ) : null}
    </CimModal>
  );
}
