// 与审批流平台后端 DTO 对齐的前端类型（字段名须与 server/dto 完全一致）。

export interface ProcessDefinitionView {
  id: string;
  key: string;
  name: string;
  version: number;
  tenantId: string;
  /** 停用后不可发起新实例。 */
  suspended?: boolean;
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
  /** Flowable 委派状态：PENDING=受托人待归还。 */
  delegationState?: string | null;
  /** 当前轮次（第几轮审批；退回重提后递增），发起为 1。 */
  round?: number;
  /** 业务摘要字段（G5）：卡片一眼可见的关键业务项。 */
  displayFields?: DisplayField[];
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
  /** 当前轮次（退回重提后递增）。 */
  round?: number;
  /** 是否等待发起人「修改并重新提交」（退回到发起人后的重提态）。 */
  awaitingResubmit?: boolean;
  /** 业务摘要字段（G5）：我发起的列表副标题。 */
  displayFields?: DisplayField[];
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
  /** G2 二次确认：重复注册同 appCode 时首调返回 true，需携带 confirmToken 再次提交 */
  confirmRequired?: boolean;
  /** G2 二次确认令牌（confirmRequired=true 时非空） */
  confirmToken?: string;
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
  /** 当前轮次（退回重提后递增）。 */
  round?: number;
  /** 是否等待发起人「修改并重新提交」。 */
  awaitingResubmit?: boolean;
  /** 业务摘要字段（G5）：详情页顶部摘要。 */
  displayFields?: DisplayField[];
}

/** 业务摘要字段（G5 闸门出口，对应后端 DisplayField）。 */
export interface DisplayField {
  key: string;
  label?: string | null;
  value?: string | null;
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
  /** 是否作为业务摘要（G5 兜底）：true 时在未传 displayFields 时取该字段作摘要 */
  display?: boolean | null;
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
  /** 业务摘要字段（可选，G5）：卡片/列表/审计一眼可见的关键项。 */
  displayFields?: DisplayField[] | null;
  /** 角色选人（G6 / D8）：节点 id → 角色 key 列表；发起时平台据此建立候选组。 */
  candidateGroups?: Record<string, string[]> | null;
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

/** 退回弹窗数据源（GET /tasks/{taskId}/return-targets）。 */
export interface ReturnTargetsView {
  /** 上一用户任务节点（默认目标）。 */
  previousNodeId: string | null;
  previousNodeName: string | null;
  /** 首节点 id（退到发起人=重提模式的目标）。 */
  initiatorNodeId: string;
  initiatorNodeName: string;
  /** 首节点是否为多人（会签）节点：true 时不提供退到发起人。 */
  initiatorNodeMulti: boolean;
  /** 已审批过的用户节点（不含首节点，供「指定节点」选择）。 */
  historicNodes: { id: string; name: string }[];
  /** 当前轮次。 */
  currentRound: number;
  /** 退回上限。 */
  maxRounds: number;
  /** 实例当前是否处于并行段（并行段内退回目标受限）。 */
  parallelActive: boolean;
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
  /** G2 二次确认令牌：重复注册时由后端签发，前端回填后再次提交以覆盖既有配置 */
  confirmToken?: string;
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

// 审批效率统计（Dashboard）。
export interface StatsView {
  todoCount: number;
  overdueCount: number;
  avgCompleteHours: number | null;
  rejectRate: number | null;
  workloads: { assignee: string; done: number; todo: number }[];
  trend: { date: string; started: number; completed: number }[];
}
