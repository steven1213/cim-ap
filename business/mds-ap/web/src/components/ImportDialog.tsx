import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { CimButton, CimModal, useToast } from '@cim/ui';
import * as api from '@/lib/mdsApi';
import type { EdcImportReport } from '@/lib/mdsApi';

/**
 * EDC Excel 导入弹窗（共享组件）——「下载模板 → 填好 → 选择文件 → 导入」主动线。
 *
 * 导入报告逐行展示成功/失败（失败含行号与原因）；导入成功后回调 onDone 刷新列表。
 * 逐条新建能力保留在页面工具栏「新建」按钮，导入面向批量建档。
 */
export default function ImportDialog({
  open,
  onClose,
  titleKey,
  kind,
  targetId,
  templateName,
  onDone,
}: {
  open: boolean;
  onClose: () => void;
  titleKey: string;
  kind: string;
  targetId?: string | null;
  templateName: string;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const toast = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [report, setReport] = useState<EdcImportReport | null>(null);

  const pick = () => fileRef.current?.click();

  const onFile = async (file: File | null) => {
    if (!file) return;
    setBusy(true);
    setReport(null);
    try {
      const b64 = await api.fileToBase64(file);
      const r = await api.importEdcExcel(kind, b64, targetId);
      setReport(r);
      if (r.failed === 0) toast.success(t('mds.edc.import.allOk', { n: r.success }));
      else toast.error(t('mds.edc.import.partial', { ok: r.success, fail: r.failed }));
      if (r.success > 0) onDone();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <CimModal
      open={open}
      onClose={() => {
        setReport(null);
        onClose();
      }}
      titleKey={titleKey}
      width={560}
      footer={null}
    >
      <div className="edc-import">
        <div className="edc-import-bar">
          <CimButton size="small" onClick={() => void api.downloadEdcTemplate(kind, templateName)}>
            {t('mds.import.downloadTemplate')}
          </CimButton>
          <CimButton type="primary" size="small" disabled={busy} onClick={pick}>
            {t('mds.edc.import.pickFile')}
          </CimButton>
          <input
            ref={fileRef}
            type="file"
            accept=".xlsx"
            style={{ display: 'none' }}
            onChange={(e) => void onFile(e.target.files?.[0] ?? null)}
          />
        </div>
        <p className="dim">{t('mds.edc.import.hint')}</p>
        {report && (
          <div className="edc-import-report">
            <p>
              {t('mds.edc.import.summary', {
                total: report.total,
                ok: report.success,
                fail: report.failed,
              })}
            </p>
            {report.failures.length > 0 && (
              <table className="edc-import-fails">
                <thead>
                  <tr>
                    <th>{t('mds.edc.import.colRow')}</th>
                    <th>{t('mds.edc.import.colReason')}</th>
                  </tr>
                </thead>
                <tbody>
                  {report.failures.map((f) => (
                    <tr key={`${f.row}-${f.message}`}>
                      <td className="mono">{f.row}</td>
                      <td>{f.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </div>
    </CimModal>
  );
}
