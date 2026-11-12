import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useResource } from '@cim/data';
import {
  Can,
  CimModal,
  CimSelect,
  CimTag,
  DataTable,
  FormGrid,
  Page,
  useConfirm,
  useToast,
  type DataTableColumn,
  type FormField,
  CimButton,
} from '@cim/ui';
import * as api from '@/lib/rmsApi';
import { QUALIFICATION_MANAGE } from '@/lib/permCodes';
import type { Device, DeviceArea, DeviceType, Qualification, Recipe } from '@/types';

const NO_ROWS: Qualification[] = [];
const NO_DEVICES: Device[] = [];
const NO_RECIPES: Recipe[] = [];
const NO_TYPES: DeviceType[] = [];
const NO_AREAS: DeviceArea[] = [];

/**
 * 配方-机台资格矩阵页（FR-Q1~Q4）。
 *
 * 迁移要点：页顶「资格判定」工具条并入表格工具栏（同一个动作只出现在一处）；
 * 吊销/删除从 `window.confirm` 换成 `useConfirm`；状态列由裸枚举改为带语义色的标签。
 */
export default function QualificationPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const confirm = useConfirm();

  const [show, setShow] = useState(false);
  const [checkRecipe, setCheckRecipe] = useState('');
  const [checkDevice, setCheckDevice] = useState('');
  const [checkResult, setCheckResult] = useState('');

  const { data, loading, error, refresh } = useResource(() => api.listQualifications());
  const rows = data ?? NO_ROWS;

  const devices = useResource(() => api.listDevices()).data ?? NO_DEVICES;
  const recipes = useResource(() => api.listRecipes()).data ?? NO_RECIPES;
  const types = useResource(() => api.listDeviceTypes()).data ?? NO_TYPES;
  const areas = useResource(() => api.listDeviceAreas()).data ?? NO_AREAS;

  const deviceOptions = useMemo(
    () => devices.map((d) => ({ value: d.id, label: d.code, hint: d.name })),
    [devices],
  );
  const recipeOptions = useMemo(
    () => recipes.map((r) => ({ value: r.id, label: r.code, hint: r.name })),
    [recipes],
  );

  const revoke = async (x: Qualification) => {
    const ok = await confirm({
      title: t('rms.common.revoke'),
      contentKey: 'rms.qual.revokeConfirm',
      danger: true,
      okKey: 'rms.common.revoke',
    });
    if (!ok) return;
    try {
      await api.revokeQualification(x.id);
      toast.success(t('rms.common.updated'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const doDelete = async (x: Qualification) => {
    const ok = await confirm({
      title: t('rms.common.delete'),
      contentKey: 'rms.common.confirmDelete',
      danger: true,
      okKey: 'rms.common.delete',
    });
    if (!ok) return;
    try {
      await api.deleteQualification(x.id);
      toast.success(t('rms.common.deleted'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
      return;
    }
    refresh();
  };

  const runCheck = async () => {
    if (!checkRecipe || !checkDevice) return;
    try {
      const r = await api.checkQualification(checkRecipe, checkDevice);
      setCheckResult(`${r.result}${r.reason ? ' · ' + r.reason : ''}`);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    }
  };

  const columns = useMemo<Array<DataTableColumn<Qualification>>>(
    () => [
      {
        key: 'recipe',
        titleKey: 'rms.qual.recipe',
        mono: true,
        width: 170,
        render: (r) => r.recipeCode ?? r.recipeId,
      },
      {
        key: 'device',
        titleKey: 'rms.qual.device',
        width: 220,
        render: (r) =>
          r.deviceCode ??
          (r.deviceTypeName
            ? `${t('rms.qual.deviceType')}: ${r.deviceTypeName}`
            : (r.areaId ?? t('rms.common.dash'))),
      },
      {
        key: 'versionPolicy',
        titleKey: 'rms.qual.versionPolicy',
        width: 130,
        render: (r) => t(`rms.qual.policy.${r.versionPolicy}`),
      },
      {
        key: 'basis',
        titleKey: 'rms.qual.basis',
        render: (r) => <span className="muted">{r.basis ?? t('rms.common.dash')}</span>,
      },
      {
        key: 'status',
        titleKey: 'rms.qual.status',
        width: 100,
        render: (r) => (
          <CimTag tone={r.status === 'GRANTED' ? 'ok' : 'err'}>
            {r.status === 'GRANTED'
              ? t('rms.qual.status.GRANTED')
              : r.status === 'REVOKED'
                ? t('rms.qual.status.REVOKED')
                : r.status}
          </CimTag>
        ),
      },
      {
        key: '__ops',
        titleKey: 'rms.common.actions',
        width: 160,
        render: (r) => (
          <span className="cell-actions">
            <Can perm={QUALIFICATION_MANAGE}>
              {r.status === 'GRANTED' && (
                <CimButton size="small" onClick={() => void revoke(r)}>
                  {t('rms.common.revoke')}
                </CimButton>
              )}
              <CimButton size="small" onClick={() => void doDelete(r)}>
                {t('rms.common.delete')}
              </CimButton>
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
      

      <DataTable<Qualification>
        headActions={<Can perm={QUALIFICATION_MANAGE}>
            <CimButton type="primary" size="small" onClick={() => setShow(true)}>
              + {t('rms.qual.grant')}
            </CimButton>
          </Can>}
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id}
        loading={loading}
        error={error?.message}
        onRetry={refresh}
        onRefresh={refresh}
        settingsKey="rms.qualification"
        scrollX="max-content"
        actions={
          <>
            <CimSelect
              value={checkRecipe || null}
              onChange={(v) => setCheckRecipe(typeof v === 'string' ? v : '')}
              options={recipeOptions}
              width={200}
              placeholderKey="rms.qual.recipe"
            />
            <CimSelect
              value={checkDevice || null}
              onChange={(v) => setCheckDevice(typeof v === 'string' ? v : '')}
              options={deviceOptions}
              width={180}
              placeholderKey="rms.qual.device"
            />
            <CimButton
              size="small"
              disabled={!checkRecipe || !checkDevice}
              onClick={() => void runCheck()}
            >
              {t('rms.qual.check')}
            </CimButton>
            {checkResult && (
              <CimTag tone={checkResult.startsWith('QUALIFIED') ? 'ok' : 'err'}>{checkResult}</CimTag>
            )}
          </>
        }
        emptyTitleKey="rms.qual.empty"
      />

      {show && (
        <GrantModal
          recipes={recipeOptions}
          devices={deviceOptions}
          types={types}
          areas={areas}
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

interface GrantForm {
  recipeId: string;
  versionPolicy: string;
  mode: 'DEVICE' | 'TYPE' | 'AREA';
  deviceId: string;
  deviceTypeId: string;
  areaId: string;
  basis: string;
}

function GrantModal({
  recipes,
  devices,
  types,
  areas,
  onClose,
  onDone,
}: {
  recipes: Array<{ value: string; label: string; hint?: string }>;
  devices: Array<{ value: string; label: string; hint?: string }>;
  types: DeviceType[];
  areas: DeviceArea[];
  onClose: () => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState<GrantForm>({
    recipeId: '',
    versionPolicy: 'LATEST_ACTIVE',
    mode: 'DEVICE',
    deviceId: '',
    deviceTypeId: '',
    areaId: '',
    basis: '',
  });
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<keyof GrantForm, string>>>({});
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  const fields = useMemo<Array<FormField<GrantForm>>>(() => {
    // 授权目标随 mode 换 key：三个候选共用一个位置，切模式时清空另两个（见下面的 onChange）
    const target: FormField<GrantForm> =
      form.mode === 'DEVICE'
        ? { kind: 'select', key: 'deviceId', labelKey: 'rms.qual.device', options: devices }
        : form.mode === 'TYPE'
          ? {
              kind: 'select',
              key: 'deviceTypeId',
              labelKey: 'rms.qual.deviceType',
              options: types.map((z) => ({ value: z.id, label: z.name })),
            }
          : {
              kind: 'select',
              key: 'areaId',
              labelKey: 'rms.qual.area',
              options: areas.map((z) => ({ value: z.id, label: z.name })),
            };

    return [
      { kind: 'select', key: 'recipeId', labelKey: 'rms.qual.recipe', required: true, options: recipes },
      {
        kind: 'select',
        key: 'versionPolicy',
        labelKey: 'rms.qual.versionPolicy',
        options: [
          { value: 'LATEST_ACTIVE', labelKey: 'rms.qual.policy.LATEST_ACTIVE' },
          { value: 'EXACT_VERSION', labelKey: 'rms.qual.policy.EXACT_VERSION' },
        ],
      },
      {
        kind: 'radio',
        key: 'mode',
        labelKey: 'rms.deviceAuth.target',
        button: true,
        options: [
          { value: 'DEVICE', labelKey: 'rms.qual.device' },
          { value: 'TYPE', labelKey: 'rms.qual.deviceType' },
          { value: 'AREA', labelKey: 'rms.qual.area' },
        ],
      },
      target,
      { kind: 'text', key: 'basis', labelKey: 'rms.qual.basis', span: 2 },
    ];
  }, [form.mode, recipes, devices, types, areas]);

  async function submit() {
    const next: Partial<Record<keyof GrantForm, string>> = {};
    if (!form.recipeId) next.recipeId = t('rms.common.required');
    setFieldErrors(next);
    if (Object.keys(next).length) return;

    setBusy(true);
    setErr('');
    try {
      await api.grantQualification({
        recipeId: form.recipeId,
        versionPolicy: form.versionPolicy,
        deviceId: form.mode === 'DEVICE' ? form.deviceId || null : null,
        deviceTypeId: form.mode === 'TYPE' ? form.deviceTypeId || null : null,
        areaId: form.mode === 'AREA' ? form.areaId || null : null,
        basis: form.basis || null,
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
      titleKey="rms.qual.grant"
      onOk={() => void submit()}
      okKey="rms.common.save"
      cancelKey="rms.common.cancel"
      okLoading={busy}
      width={640}
    >
      <FormGrid<GrantForm>
        fields={fields}
        value={form}
        onChange={(patch) =>
          setForm((prev) => {
            const next = { ...prev, ...patch };
            // 换授权模式时清掉另外两个目标的取值，避免「看不见但带着走」
            if (patch.mode && patch.mode !== prev.mode) {
              next.deviceId = '';
              next.deviceTypeId = '';
              next.areaId = '';
            }
            return next;
          })
        }
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
