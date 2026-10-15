// 与审批流平台后端 DTO 对齐的前端类型（字段名须与 server/dto 完全一致）。

export interface ProcessDefinitionView {
  id: string;
  key: string;
  name: string;
  version: number;
  tenantId: string;
}

export interface TaskView {
  taskId: string;
  processInstanceId: string;
  processKey: string;
  businessKey: string;
  title: string | null;
  tenantId: string;
  name: string;
  assignee: string | null;
  candidateUsers: string[];
  outcome: string | null;
  createdAt: string;
  dueDate: string | null;
  /** 超出超时阈值的分钟数；未超时为 null。 */
  overdueMinutes?: number | null;
}

export interface ProcessInstanceView {
  processInstanceId: string;
  businessKey: string;
  processKey: string;
  title: string | null;
  tenantId: string;
  status: string; // ACTIVE | COMPLETED | WITHDRAWN
  startedAt: string;
  endedAt: string | null;
  /** 当前待办最大超时分钟数（仅 ACTIVE 且超时时非空）。 */
  overdueMinutes?: number | null;
}

export interface DefinitionXmlView {
  definitionId: string;
  key: string;
  name: string;
  version: number;
  bpmnXml: string;
}

export interface CallbackRegistrationView {
  id: string;
  appCode: string;
  baseUrl: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface OutboxView {
  id: string;
  messageId: string;
  appCode: string;
  businessKey: string;
  eventType: string;
  status: string; // PENDING | SENT | DEAD
  attempts: number;
  createdAt: string;
  sentAt: string | null;
  lastError: string | null;
}

export interface AuditView {
  id: string;
  seq: number;
  businessKey: string;
  processInstanceId: string;
  taskId: string;
  action: string;
  actor: string;
  beforeState: string;
  afterState: string;
  detail: string;
  prevHash: string;
  hash: string;
  createdAt: string;
}

export interface AttachmentView {
  id: string;
  businessKey: string;
  processInstanceId: string | null;
  fileName: string;
  contentType: string | null;
  sizeBytes: number;
  uploader: string;
  uploadedAt: string;
}

export interface ProcessDetailView {
  processInstanceId: string;
  businessKey: string;
  processKey: string;
  definitionName: string | null;
  title: string | null;
  initiator: string | null;
  tenantId: string | null;
  status: string; // ACTIVE | COMPLETED
  startedAt: string | null;
  endedAt: string | null;
  formData: Record<string, unknown>;
  /** 表单字段描述：详情页据此渲染中文标签/分组/类型化值 */
  formSchema: FormSchemaField[];
  activeNodes: string[];
  activeTasks: TaskView[];
  history: AuditView[];
  attachments: AttachmentView[];
}

// ---------- 请求体 ----------

/** 审批内容字段描述（对接外部系统时随 formData 传入） */
export interface FormSchemaField {
  key: string;
  label?: string | null;
  /** text | textarea | number | money | boolean | date | datetime | list */
  type?: string | null;
  unit?: string | null;
  group?: string | null;
  required?: boolean | null;
  order?: number | null;
}

export interface StartProcessRequest {
  processKey: string;
  businessKey: string;
  tenantId: string;
  initiator: string;
  title: string;
  approvers?: string[] | null;
  assignee?: string | null;
  variables?: Record<string, unknown> | null;
  formData?: Record<string, unknown> | null;
  formSchema?: FormSchemaField[] | null;
}

export interface CompleteTaskRequest {
  outcome: 'APPROVE' | 'REJECT';
  comment?: string | null;
  actor: string;
}

export interface ReturnRequest {
  targetActivityId?: string | null;
  reason?: string | null;
  actor: string;
}

export interface AddSignRequest {
  assignee: string;
  type: 'AFTER' | 'BEFORE';
  reason?: string | null;
  actor: string;
}

export interface ReassignRequest {
  newAssignee: string;
  reason?: string | null;
  actor: string;
}

export interface CallbackRegistrationRequest {
  appCode: string;
  baseUrl: string;
  secret?: string | null;
}

// SSE 通知负载（NEW_TASK：任务创建/指派；URGE：发起人催办）。
export interface NotificationEvent {
  taskId?: string;
  name?: string;
  processInstanceId?: string;
  type?: string;
  userId?: string;
  /** URGE 专用 */
  title?: string;
  businessKey?: string;
  from?: string;
}
