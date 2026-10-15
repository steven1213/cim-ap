// 审批流平台 API 客户端。
//
// 后端所有接口位于 /api/v1/approval 前缀下（见 server 各 Controller 的 @RequestMapping）。
// 后端为 dev 态：成功响应为**裸数据**（未套 Result 包装），仅异常时由平台全局异常处理器
// 包装为 {code,msg,...}（或 Spring 默认错误体）。故本客户端：成功直接返回解析后的数据；
// 失败时对两种错误体都能提取可读 message。
import axios, { type AxiosInstance } from 'axios';
import { useIdentityStore } from '@/store/identityStore';
import type {
  AddSignRequest,
  AuditView,
  CallbackRegistrationRequest,
  CallbackRegistrationView,
  CompleteTaskRequest,
  DefinitionXmlView,
  OutboxView,
  ProcessDefinitionView,
  ProcessDetailView,
  ProcessInstanceView,
  ReassignRequest,
  ReturnRequest,
  StartProcessRequest,
  StatsView,
  TaskView,
  AttachmentView,
} from '@/types';

export interface ApiError extends Error {
  code?: number;
  errors?: Record<string, string>;
}

const http: AxiosInstance = axios.create({ baseURL: '/api/v1/approval' });

http.interceptors.response.use(
  (resp) => {
    // 平台统一返回结构（platform README §4）：{code, msg, data, ...}
    // code===0 视为成功，把 data 解包给调用方；非 0 统一抛错（HTTP 状态可能为 200）
    const body = resp?.data;
    if (body && typeof body === 'object' && 'code' in body && 'data' in body) {
      if (body.code !== 0) {
        const e: ApiError = new Error(body.msg || '请求失败');
        e.code = body.code;
        e.errors = body.errors;
        return Promise.reject(e);
      }
      resp.data = body.data;
    }
    return resp;
  },
  (error) => {
    const data = error?.response?.data;
    let message = error?.message || '请求失败';
    if (data && typeof data === 'object') {
      if ('code' in data && data.code !== 0 && 'msg' in data) {
        message = data.msg;
      } else if ('message' in data && typeof data.message === 'string') {
        message = data.message;
      }
    }
    const e: ApiError = new Error(message);
    e.code = data?.code;
    e.errors = data?.errors;
    return Promise.reject(e);
  },
);

function currentIdentity(): { userId: string; tenantId: string } {
  const { userId, tenantId } = useIdentityStore.getState();
  return { userId, tenantId };
}

async function apiGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const r = await http.get<T>(url, { params });
  return r.data;
}

async function apiPost<T>(url: string, body?: unknown): Promise<T> {
  const r = await http.post<T>(url, body);
  return r.data;
}

// ---------- 流程定义 / 发起 ----------

export function listDefinitions(): Promise<ProcessDefinitionView[]> {
  return apiGet<ProcessDefinitionView[]>('/definitions');
}

export function getDefinitionXml(definitionId: string): Promise<DefinitionXmlView> {
  return apiGet<DefinitionXmlView>(`/processes/${encodeURIComponent(definitionId)}/xml`);
}

export function startProcess(req: StartProcessRequest): Promise<{ processInstanceId: string }> {
  return apiPost<{ processInstanceId: string }>('/processes/start', req);
}

/** 撤回：发起人终止运行中实例。 */
export function withdrawInstance(processInstanceId: string, actor?: string): Promise<void> {
  return http.post(`/processes/instances/${encodeURIComponent(processInstanceId)}/withdraw`, null, {
    params: { actor: actor ?? currentIdentity().userId },
  });
}

/** 催办：向当前待办人推送实时催办通知。 */
export function urgeInstance(processInstanceId: string, actor?: string): Promise<void> {
  return http.post(`/processes/instances/${encodeURIComponent(processInstanceId)}/urge`, null, {
    params: { actor: actor ?? currentIdentity().userId },
  });
}

// ---------- 运行时部署（拖拽设计器导出） ----------

export interface DeployProcessRequest {
  processKey: string;
  processName: string;
  bpmnXml: string;
}

export function deployProcess(req: DeployProcessRequest): Promise<ProcessDefinitionView> {
  return apiPost<ProcessDefinitionView>('/processes/deploy', req);
}

// ---------- 任务 ----------

export function listTodo(userId?: string, tenantId?: string): Promise<TaskView[]> {
  const id = userId ?? currentIdentity().userId;
  const tn = tenantId ?? currentIdentity().tenantId;
  return apiGet<TaskView[]>('/tasks/todo', { userId: id, tenantId: tn });
}

export function listDone(userId?: string, tenantId?: string): Promise<TaskView[]> {
  const id = userId ?? currentIdentity().userId;
  const tn = tenantId ?? currentIdentity().tenantId;
  return apiGet<TaskView[]>('/tasks/done', { userId: id, tenantId: tn });
}

export function listInitiated(userId?: string, tenantId?: string): Promise<ProcessInstanceView[]> {
  const id = userId ?? currentIdentity().userId;
  const tn = tenantId ?? currentIdentity().tenantId;
  return apiGet<ProcessInstanceView[]>('/tasks/initiated', { userId: id, tenantId: tn });
}

export function listTodoPaged(page1: number, size: number): Promise<PageResult<TaskView>> {
  const { userId, tenantId } = currentIdentity();
  return apiGet<PageResult<TaskView>>(`/tasks/todo/page?page=${page1}&size=${size}`, { userId, tenantId });
}
export function listDonePaged(page1: number, size: number): Promise<PageResult<TaskView>> {
  const { userId, tenantId } = currentIdentity();
  return apiGet<PageResult<TaskView>>(`/tasks/done/page?page=${page1}&size=${size}`, { userId, tenantId });
}
export function listInitiatedPaged(page1: number, size: number): Promise<PageResult<ProcessInstanceView>> {
  const { userId, tenantId } = currentIdentity();
  return apiGet<PageResult<ProcessInstanceView>>(`/processes/initiated/page?page=${page1}&size=${size}`, { userId, tenantId });
}
// 平台分页结构（cim-core PageResult：records/total/page/size）
export interface PageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}
export function batchComplete(body: { taskIds: string[]; outcome: string; comment?: string; actor: string })
  : Promise<{ taskId: string; ok: boolean; error: string | null }[]> {
  return apiPost('/tasks/batch-complete', body);
}
export function delegateTask(taskId: string, body: { assignee: string; reason?: string; actor: string }): Promise<void> {
  return apiPost(`/tasks/${encodeURIComponent(taskId)}/delegate`, body);
}
export function resolveTask(taskId: string, body: { comment?: string; actor: string }): Promise<void> {
  return apiPost(`/tasks/${encodeURIComponent(taskId)}/resolve`, body);
}
export function getStats(days = 30): Promise<StatsView> {
  return apiGet(`/stats?days=${days}`);
}
export function suspendDefinition(definitionId: string, suspend: boolean): Promise<void> {
  return apiPost(`/processes/${encodeURIComponent(definitionId)}/suspend?suspend=${suspend}`, null);
}
export function duplicateDefinition(definitionId: string): Promise<ProcessDefinitionView> {
  return apiPost<ProcessDefinitionView>(`/processes/${encodeURIComponent(definitionId)}/duplicate`);
}
export function getOutboxStats(): Promise<Record<string, number>> {
  return apiGet('/callbacks/outbox/stats');
}
export function testCallback(appCode: string): Promise<{ messageId: string; status: string; lastError: string | null }> {
  return apiPost(`/callbacks/test/${encodeURIComponent(appCode)}`);
}
export function completeTask(taskId: string, req: CompleteTaskRequest): Promise<void> {
  return apiPost<void>(`/tasks/${taskId}/complete`, req);
}

export function returnTask(taskId: string, req: ReturnRequest): Promise<void> {
  return apiPost<void>(`/tasks/${taskId}/return`, req);
}

export function addSignTask(taskId: string, req: AddSignRequest): Promise<void> {
  return apiPost<void>(`/tasks/${taskId}/add-sign`, req);
}

export function reassignTask(taskId: string, req: ReassignRequest): Promise<void> {
  return apiPost<void>(`/tasks/${taskId}/reassign`, req);
}

// ---------- 回调注册 / 查询 ----------

export function registerCallback(req: CallbackRegistrationRequest): Promise<CallbackRegistrationView> {
  return apiPost<CallbackRegistrationView>('/callbacks/register', req);
}

export function listCallbacks(): Promise<CallbackRegistrationView[]> {
  return apiGet<CallbackRegistrationView[]>('/callbacks');
}

export function queryOutbox(
  appCode?: string,
  businessKey?: string,
): Promise<OutboxView[]> {
  return apiGet<OutboxView[]>('/callbacks/outbox', { appCode, businessKey });
}

export function deliverMessage(messageId: string): Promise<void> {
  return apiPost<void>(`/callbacks/deliver/${messageId}`);
}

// ---------- 审批详情 / 附件 ----------

export function getProcessDetail(processInstanceId: string, userId?: string): Promise<ProcessDetailView> {
  return apiGet<ProcessDetailView>(`/processes/instances/${encodeURIComponent(processInstanceId)}/detail`, {
    userId: userId ?? currentIdentity().userId,
  });
}

export function listAttachments(businessKey: string): Promise<AttachmentView[]> {
  return apiGet<AttachmentView[]>('/attachments', { businessKey });
}

export async function uploadAttachment(
  file: File,
  businessKey: string,
  opts?: { processInstanceId?: string; tenantId?: string; uploader?: string },
): Promise<AttachmentView> {
  const form = new FormData();
  form.append('file', file);
  form.append('businessKey', businessKey);
  if (opts?.processInstanceId) form.append('processInstanceId', opts.processInstanceId);
  form.append('tenantId', opts?.tenantId ?? currentIdentity().tenantId);
  form.append('uploader', opts?.uploader ?? currentIdentity().userId);
  const r = await http.post<AttachmentView>('/attachments/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return r.data;
}

export function deleteAttachment(id: string, actor?: string): Promise<void> {
  return http.delete(`/attachments/${encodeURIComponent(id)}`, {
    params: { actor: actor ?? currentIdentity().userId },
  }).then(() => undefined);
}

export function attachmentDownloadUrl(id: string): string {
  return `/api/v1/approval/attachments/${encodeURIComponent(id)}/download`;
}

// ---------- 审计 ----------

export function listAudit(businessKey: string): Promise<AuditView[]> {
  return apiGet<AuditView[]>('/audit', { businessKey });
}

// ---------- SSE 通知 ----------
//
// 后端 dev 态以 userId 查询参数区分接收者；返回 EventSource 供组件管理生命周期。
export function notificationsStreamUrl(userId: string): string {
  const base = import.meta.env.MODE === 'production' ? '' : '';
  const params = new URLSearchParams({ userId });
  return `${base}/api/v1/approval/notifications/stream?${params.toString()}`;
}

export default {
  listDefinitions,
  getDefinitionXml,
  startProcess,
  deployProcess,
  listTodo,
  listDone,
  listInitiated,
  completeTask,
  returnTask,
  addSignTask,
  reassignTask,
  registerCallback,
  listCallbacks,
  queryOutbox,
  deliverMessage,
  listAudit,
  getProcessDetail,
  listAttachments,
  uploadAttachment,
  deleteAttachment,
  attachmentDownloadUrl,
  notificationsStreamUrl,
};
