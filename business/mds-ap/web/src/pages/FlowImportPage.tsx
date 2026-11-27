import { useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Can,
  CimButton,
  DataTable,
  Page,
  Panel,
  useToast,
  type DataTableColumn,
} from '@cim/ui';
import * as api from '@/lib/mdsApi';
import { MDS_PERMS } from '@/lib/permCodes';

type Phase = 'pick' | 'preview' | 'done';

interface ErrRow {
  sheet: string;
  row: number;
  message: string;
}

/**
 * 流程三段式导入向导（FlowImportPage，P2/P07）——对标 myCIM Upload 抽屉的三步动线：
 * ① 下载模板（4 sheet：01_流程/02_节点/03_指派/04_参数）→ ② 选择文件 dryRun 校验预览
 * （错误清单逐条定位 sheet/行）→ ③ 确认提交（事务落库，返回各段计数）。
 * 覆盖三层 ContextChain：指派/参数 sheet 的 scope=OPERATION/FLOW/STEP。
 */
export default function FlowImportPage() {
  const { t } = useTranslation();
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);

  const [phase, setPhase] = useState<Phase>('pick');
  const [fileName, setFileName] = useState('');
  const [dataBase64, setDataBase64] = useState('');
  const [preview, setPreview] = useState<api.FlowImportPreview | null>(null);
  const [result, setResult] = useState<api.FlowImportResult | null>(null);
  const [busy, setBusy] = useState(false);

  const canImport = MDS_PERMS.flow.update;

  const pick = () => fileRef.current?.click();

  const onFile = async (f: File | null) => {
    if (!f) return;
    setFileName(f.name);
    setBusy(true);
    try {
      const b64 = await api.fileToBase64(f);
      setDataBase64(b64);
      const p = await api.importFlows(b64, true);
      setPreview(p as api.FlowImportPreview);
      setResult(null);
      setPhase('preview');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const commit = async () => {
    if (!dataBase64) return;
    const ok = await confirmCommit();
    if (!ok) return;
    setBusy(true);
    try {
      const r = await api.importFlows(dataBase64, false);
      setResult(r as api.FlowImportResult);
      setPhase('done');
      toast.success(t('mds.import.doneToast'));
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const confirmCommit = async (): Promise<boolean> => {
    // 提交前再跑一次 dryRun（文件可能已被处理），有错直接刷新预览
    try {
      const p = await api.importFlows(dataBase64, true);
      const pv = p as api.FlowImportPreview;
      if (pv.errors?.length) {
        setPreview(pv);
        setPhase('preview');
        toast.error(t('mds.import.stillHasErrors'));
        return false;
      }
      return true;
    } catch {
      return true; // 预检失败不阻塞提交（提交端有原子事务兜底）
    }
  };

  const reset = () => {
    setPhase('pick');
    setFileName('');
    setDataBase64('');
    setPreview(null);
    setResult(null);
    if (fileRef.current) fileRef.current.value = '';
  };

  const errColumns = useMemo<Array<DataTableColumn<ErrRow>>>(
    () => [
      { key: 'sheet', titleKey: 'mds.import.col.sheet', width: 120, mono: true },
      { key: 'row', titleKey: 'mds.import.col.row', width: 70 },
      { key: 'message', titleKey: 'mds.import.col.message', ellipsis: true },
      // eslint-disable-next-line react-hooks/exhaustive-deps
    ], []);

  const errors: ErrRow[] = preview?.errors ?? [];

  return (
    <Page>
      <Panel>
        <input
          ref={fileRef}
          type="file"
          accept=".xlsx"
          style={{ display: 'none' }}
          onChange={(e) => void onFile(e.target.files?.[0] ?? null)}
        />
        <div className="import-steps">
          <div className={`import-step ${phase === 'pick' ? 'active' : ''}`}>
            <h3>{t('mds.import.step1Title')}</h3>
            <p className="dim">{t('mds.import.step1Desc')}</p>
            <div className="op-detail-actions">
              <CimButton size="small" onClick={() => void api.downloadImportTemplate()}>
                {t('mds.import.downloadTemplate')}
              </CimButton>
              <Can perm={canImport}>
                <CimButton type="primary" size="small" disabled={busy} onClick={pick}>
                  {t('mds.import.pickFile')}
                </CimButton>
              </Can>
              {fileName && <span className="mono dim">{fileName}</span>}
            </div>
          </div>
          {phase !== 'pick' && (
            <div className={`import-step ${phase === 'preview' ? 'active' : ''}`}>
              <h3>{t('mds.import.step2Title')}</h3>
              <p className="dim">{t('mds.import.step2Desc')}</p>
              <p>
                <span className="mono">{t('mds.import.count.flow', { n: preview?.flowRows ?? 0 })}</span>
                {' · '}
                <span className="mono">{t('mds.import.count.step', { n: preview?.stepRows ?? 0 })}</span>
                {' · '}
                <span className="mono">{t('mds.import.count.assign', { n: preview?.assignRows ?? 0 })}</span>
                {' · '}
                <span className="mono">{t('mds.import.count.attr', { n: preview?.attrRows ?? 0 })}</span>
              </p>
            </div>
          )}
          {phase === 'done' && (
            <div className="import-step active">
              <h3>{t('mds.import.step3Title')}</h3>
              <p className="dim">{t('mds.import.step3Desc')}</p>
              <p>
                <span className="mono">{t('mds.import.result.flowCreated', { n: result?.flowsCreated ?? 0 })}</span>
                {' · '}
                <span className="mono">{t('mds.import.result.flowUpdated', { n: result?.flowsUpdated ?? 0 })}</span>
                {' · '}
                <span className="mono">{t('mds.import.result.steps', { n: result?.stepsImported ?? 0 })}</span>
                {' · '}
                <span className="mono">{t('mds.import.result.assigns', { n: result?.assignsImported ?? 0 })}</span>
                {' · '}
                <span className="mono">{t('mds.import.result.attrs', { n: result?.attrsImported ?? 0 })}</span>
              </p>
            </div>
          )}
        </div>
        {errors.length > 0 && (
          <div className="icm-detail" style={{ marginTop: 12 }}>
            <DataTable<ErrRow>
              titleKey="mds.import.errorsTitle"
              columns={errColumns}
              rows={errors}
              rowKey={(r) => `${r.sheet}-${r.row}-${r.message}`}
              emptyTitleKey="mds.import.noErrors"
            />
          </div>
        )}
        <div className="op-detail-actions" style={{ marginTop: 12 }}>
          {phase === 'preview' && (
            <>
              <Can perm={canImport}>
                <CimButton
                  type="primary"
                  size="small"
                  disabled={busy || errors.length > 0}
                  onClick={() => void commit()}
                >
                  {t('mds.import.commit')}
                </CimButton>
              </Can>
              <CimButton size="small" onClick={reset}>{t('mds.common.cancel')}</CimButton>
            </>
          )}
          {phase === 'done' && (
            <CimButton size="small" onClick={reset}>{t('mds.import.importMore')}</CimButton>
          )}
        </div>
        <p className="dim" style={{ marginTop: 12 }}>{t('mds.import.hint')}</p>
      </Panel>
    </Page>
  );
}
