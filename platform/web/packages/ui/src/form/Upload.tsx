// 文件上传：只负责「选文件 + 校验 + 列表展示」，**不发请求**。
//
// 这是刻意的边界。参考项目的 GoatUploadPro（198 行）把请求、进度、裁剪、
// 预览全塞进去，结果每个业务的鉴权头、分片规则、存储路径都不一样，
// 复用度接近于 0。这里只做 UI 与校验，上传由调用方在自己的 api 层做：
//
// ```tsx
// const [files, setFiles] = useState<File[]>([]);
// <CimUpload files={files} onChange={setFiles} maxSizeMb={20} accept=".pdf,.png" />
// // 提交时：await rmsApi.uploadAttachments(files)
// ```
//
// 校验（大小/数量/类型）必须在组件里做掉：后端报「文件过大」时用户已经
// 等了 30 秒，而这个错误在前端 0 秒就能拦。

import { Button, Upload } from 'antd';
import type { UploadFile } from 'antd';
import type { ReactNode } from 'react';
import { useUiLabel } from '../locale';

export interface CimUploadProps {
  /** 当前待上传的文件（受控）。 */
  files: File[];
  onChange: (files: File[]) => void;
  /** accept 语法，如 `.pdf,.png,image/*`。 */
  accept?: string;
  /** 单文件大小上限（MB），默认 20。 */
  maxSizeMb?: number;
  /** 文件数量上限，默认 10。 */
  maxCount?: number;
  multiple?: boolean;
  disabled?: boolean;
  /** 提示文案的 i18n 键（默认自动拼「≤20MB，最多 10 个」）。 */
  hintKey?: string;
  /** 校验失败回调（用于 toast）。 */
  onReject?: (reason: 'size' | 'count' | 'type', file: File) => void;
  buttonKey?: string;
  /** 完全自定义触发元素。 */
  children?: ReactNode;
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function CimUpload({
  files,
  onChange,
  accept,
  maxSizeMb = 20,
  maxCount = 10,
  multiple = true,
  disabled = false,
  hintKey,
  onReject,
  buttonKey,
  children,
}: CimUploadProps) {
  const label = useUiLabel();

  // uid 由文件自身属性决定：同名同大小同修改时间视为同一个文件（重选不会重复）
  const keyOf = (f: File) => `${f.name}:${f.size}:${f.lastModified}`;

  const fileList: UploadFile[] = files.map((f) => ({
    uid: keyOf(f),
    name: f.name,
    size: f.size,
    status: 'done',
  }));

  const hint =
    hintKey
      ? label(hintKey, '')
      : `≤ ${maxSizeMb}MB，${label('ui.upload.maxCount', '最多')} ${maxCount}`;

  return (
    <div className="cim-upload">
      <Upload
        fileList={fileList}
        multiple={multiple}
        accept={accept}
        disabled={disabled}
        // 关键：返回 false 阻止 antd 自己发请求
        beforeUpload={(file) => {
          if (files.length >= maxCount) {
            onReject?.('count', file);
            return Upload.LIST_IGNORE;
          }
          if (file.size > maxSizeMb * 1024 * 1024) {
            onReject?.('size', file);
            return Upload.LIST_IGNORE;
          }
          onChange([...files, file]);
          return Upload.LIST_IGNORE;
        }}
        onRemove={(f) => {
          const target = files.find((x) => keyOf(x) === f.uid);
          onChange(target ? files.filter((x) => x !== target) : files);
          return false;
        }}
        showUploadList={{
          showPreviewIcon: false,
          showDownloadIcon: false,
        }}
        itemRender={undefined}
      >
        {children ?? (
          <Button disabled={disabled}>{buttonKey ? label(buttonKey, '选择文件') : label('ui.upload.pick', '选择文件')}</Button>
        )}
      </Upload>
      <div className="cim-field-hint">{hint}</div>
      {files.length > 0 ? (
        <div className="cim-upload-summary">
          {label('ui.upload.total', '共')} {files.length} / {maxCount}
          {files.length > 0 ? ` · ${formatSize(files.reduce((s, f) => s + f.size, 0))}` : ''}
        </div>
      ) : null}
    </div>
  );
}
